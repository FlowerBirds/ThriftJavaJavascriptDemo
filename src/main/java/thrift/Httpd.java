/*
 * ====================================================================
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 * ====================================================================
 *
 * This software consists of voluntary contributions made by many
 * individuals on behalf of the Apache Software Foundation.  For more
 * information on the Apache Software Foundation, please see
 * <http://www.apache.org/>.
 *
 */

package thrift;

import java.io.File;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URLDecoder;
import java.util.Collection;
import java.util.Iterator;
import java.util.Locale;

import org.apache.http.ConnectionClosedException;
import org.apache.http.HttpEntity;
import org.apache.http.HttpEntityEnclosingRequest;
import org.apache.http.HttpException;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.HttpServerConnection;
import org.apache.http.HttpStatus;
import org.apache.http.MethodNotSupportedException;
import org.apache.http.entity.ContentProducer;
import org.apache.http.entity.EntityTemplate;
import org.apache.http.entity.FileEntity;
import org.apache.http.impl.DefaultHttpResponseFactory;
import org.apache.http.impl.DefaultHttpServerConnection;
import org.apache.http.impl.NoConnectionReuseStrategy;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.CoreConnectionPNames;
import org.apache.http.params.CoreProtocolPNames;
import org.apache.http.params.HttpParams;
import org.apache.http.protocol.BasicHttpContext;
import org.apache.http.protocol.BasicHttpProcessor;
import org.apache.http.protocol.HttpContext;
import org.apache.http.protocol.HttpProcessor;
import org.apache.http.protocol.HttpRequestHandler;
import org.apache.http.protocol.HttpRequestHandlerRegistry;
import org.apache.http.protocol.HttpService;
import org.apache.http.util.EntityUtils;
import org.apache.log4j.Logger;
import org.apache.thrift.TProcessor;
import org.apache.thrift.protocol.TJSONProtocol;
import org.apache.thrift.protocol.TProtocol;
import org.apache.thrift.server.TServer;
import org.apache.thrift.server.TSimpleServer;
import org.apache.thrift.server.TServer.Args;
import org.apache.thrift.transport.TMemoryBuffer;
import org.apache.thrift.transport.TServerSocket;
import org.apache.thrift.transport.TServerTransport;

import tutorial.Calculator;
import tutorial.Calculator.Iface;
import eu.medsea.mimeutil.MimeType;
import eu.medsea.mimeutil.MimeUtil2;
import eu.medsea.mimeutil.detector.ExtensionMimeDetector;

/**
 * Basic, yet fully functional and spec compliant, HTTP/1.1 file server.
 * <p>
 * Please note the purpose of this application is demonstrate the usage of
 * HttpCore APIs. It is NOT intended to demonstrate the most efficient way of
 * building an HTTP file server.
 * 
 * 
 */
public class Httpd {
	static Logger logger = Logger.getLogger("Httpd");

    public static void main(String[] args) throws Exception {
    	
        if (args.length < 1) {
        	logger.info("No root directory specified. Using working directory.");
        	args = new String[1];
        	args[0] = ".";
        }
        Thread t = new RequestListenerThread(8088, args[0]);
        t.setDaemon(false);
        t.start();
    }

    static class HttpFileHandler implements HttpRequestHandler {

        private final String docRoot;

        public HttpFileHandler(final String docRoot) {
            super();
            this.docRoot = docRoot;
        }

        public void handle(final HttpRequest request, final HttpResponse response, final HttpContext context) throws HttpException, IOException {

            String method = request.getRequestLine().getMethod().toUpperCase(Locale.ENGLISH);
            if (!method.equals("GET") && !method.equals("HEAD") && !method.equals("POST")) {
                throw new MethodNotSupportedException(method + " method not supported");
            }
            String target = request.getRequestLine().getUri();

            if (request instanceof HttpEntityEnclosingRequest && target.equals("/service")) {
                HttpEntity entity = ((HttpEntityEnclosingRequest) request).getEntity();
                byte[] entityContent = EntityUtils.toByteArray(entity);
                logger.debug("Incoming content: " + new String(entityContent));
                
                final String output = this.thriftRequest(entityContent);
                
                logger.debug("Outgoing content: "+output);
                
                EntityTemplate body = new EntityTemplate(new ContentProducer() {

                    public void writeTo(final OutputStream outstream) throws IOException {
                        OutputStreamWriter writer = new OutputStreamWriter(outstream, "UTF-8");
                        writer.write(output);
                        writer.flush();
                    }

                });
                body.setContentType("text/html; charset=UTF-8");
                response.setEntity(body);
            } else {
                if(target.indexOf("?") != -1) {
                 target = target.substring(1, target.indexOf("?"));
                }

                final File file = new File(this.docRoot, URLDecoder.decode(target, "UTF-8"));

                if (!file.exists()) {

                    response.setStatusCode(HttpStatus.SC_NOT_FOUND);
                    EntityTemplate body = new EntityTemplate(new ContentProducer() {

                        public void writeTo(final OutputStream outstream) throws IOException {
                            OutputStreamWriter writer = new OutputStreamWriter(outstream, "UTF-8");
                            writer.write("<html><body><h1>");
                            writer.write("File ");
                            writer.write(file.getPath());
                            writer.write(" not found");
                            writer.write("</h1></body></html>");
                            writer.flush();
                        }

                    });
                    body.setContentType("text/html; charset=UTF-8");
                    response.setEntity(body);
                    logger.warn("File " + file.getPath() + " not found");

                } else if (!file.canRead() || file.isDirectory()) {

                    response.setStatusCode(HttpStatus.SC_FORBIDDEN);
                    EntityTemplate body = new EntityTemplate(new ContentProducer() {

                        public void writeTo(final OutputStream outstream) throws IOException {
                            OutputStreamWriter writer = new OutputStreamWriter(outstream, "UTF-8");
                            writer.write("<html><body><h1>");
                            writer.write("Access denied");
                            writer.write("</h1></body></html>");
                            writer.flush();
                        }

                    });
                    body.setContentType("text/html; charset=UTF-8");
                    response.setEntity(body);
                    logger.warn("Cannot read file " + file.getPath());

                } else {

                    String mimeType = "application/octet-stream";
                    MimeUtil2 mimeUtil = new MimeUtil2();
                    synchronized (this) {
                        mimeUtil.registerMimeDetector(ExtensionMimeDetector.class.getName());
                    }
                    Collection<MimeType> collection = mimeUtil.getMimeTypes(file);
                    Iterator<MimeType> iterator = collection.iterator();
                    while(iterator.hasNext()) {
                        MimeType mt = iterator.next();
                        mimeType =  mt.getMediaType() + "/" + mt.getSubType();
                        break;
                    }

                    response.setStatusCode(HttpStatus.SC_OK);
                    FileEntity body = new FileEntity(file, mimeType);
                    response.addHeader("Content-Type", mimeType);
                    response.setEntity(body);
                    logger.debug("Serving file " + file.getPath());

                }
            }
        }
        
        private String thriftRequest(byte[] input){
            try{
            
                //Input
                TMemoryBuffer inbuffer = new TMemoryBuffer(input.length);           
                inbuffer.write(input);              
                TProtocol  inprotocol   = new TJSONProtocol(inbuffer);                   
                
                //Output
                TMemoryBuffer outbuffer = new TMemoryBuffer(100);           
                TProtocol outprotocol   = new TJSONProtocol(outbuffer);
                
                Iface handler = new CalculatorHandler();
                TProcessor processor = new Calculator.Processor<Iface>(handler);      
                processor.process(inprotocol, outprotocol);
                
                byte[] output = new byte[outbuffer.length()];
                outbuffer.readAll(output, 0, output.length);
            
                return new String(output,"UTF-8");
            }catch(Throwable t){
                return "Error:"+t.getMessage();
            }
             
                     
        }
        
    }

    static class RequestListenerThread extends Thread {

        private final ServerSocket httpServerSocket;
        private final ServerSocket rawServerSocket;
        private final HttpParams params;
        private final HttpService httpService;

        public RequestListenerThread(int port, final String docroot) throws IOException {
            this.httpServerSocket = new ServerSocket(port);
            this.rawServerSocket = new ServerSocket(port+1);
            this.params = new BasicHttpParams();
            this.params.setIntParameter(CoreConnectionPNames.SO_TIMEOUT, 1000).setIntParameter(CoreConnectionPNames.SOCKET_BUFFER_SIZE, 8 * 1024)
                    .setBooleanParameter(CoreConnectionPNames.STALE_CONNECTION_CHECK, false).setBooleanParameter(CoreConnectionPNames.TCP_NODELAY, true)
                    .setParameter(CoreProtocolPNames.ORIGIN_SERVER, "HttpComponents/1.1");

            // Set up the HTTP protocol processor
            HttpProcessor httpproc = new BasicHttpProcessor();

            // Set up request handlers
            HttpRequestHandlerRegistry reqistry = new HttpRequestHandlerRegistry();
            reqistry.register("*", new HttpFileHandler(docroot));

            // Set up the HTTP service
            this.httpService = new HttpService(httpproc, new NoConnectionReuseStrategy(), new DefaultHttpResponseFactory());
            this.httpService.setParams(this.params);
            this.httpService.setHandlerResolver(reqistry);
        }

        public void run() {
        	logger.info("HTTP listening on port " + this.httpServerSocket.getLocalPort());
        	logger.debug("Point your browser to http://localhost:8088/index.html");
            
        	new Thread(new Runnable() {
				
				@Override
				public void run() {
					

		        	 try {
		        		 int port  = httpServerSocket.getLocalPort()+10;
		        	      TServerTransport serverTransport = new TServerSocket(port);
		        	      
		        	      Iface handler = new CalculatorHandler();
		        	      TProcessor processor = new Calculator.Processor<Iface>(handler);      
		        	      
		        	      Args args = new Args(serverTransport)
		        	      .processor(processor)
		        	      .inputProtocolFactory(new TJSONProtocol.Factory())
		        	      .outputProtocolFactory(new TJSONProtocol.Factory());
		        	      
		        	      TServer server = new TSimpleServer(args);
		        	      // Use this for a multithreaded server
		        	      // TServer server = new TThreadPoolServer(new TThreadPoolServer.Args(serverTransport).processor(processor));

		        	      logger.info("Raw listening on port " + port);
		        	      server.serve();
		        	    } catch (Exception e) {
		        	      e.printStackTrace();
		        	    }
						
					
				} }
				
        			).start();
        	 
//        	new Thread(new Runnable() {
//				
//				@Override
//				public void run() {
//					while (!Thread.interrupted()) {
//		                try {
//		                    // Set up HTTP connection
//		                    Socket socket = rawServerSocket.accept();
//		                    ServerSocket conn = new ServerSocket()
//		                    DefaultHttpServerConnection conn = new DefaultHttpServerConnection();
//		                    logger.debug("Incoming connection from " + socket.getInetAddress());
//		                    HttpParams params = new ;
//		                    conn.bind(socket, params);
//
//		                    // Start worker thread
//		                    Thread t = new RawWorkerThread(this.httpService, conn);
//		                    t.setDaemon(true);
//		                    t.start();
//		                } catch (InterruptedIOException ex) {
//		                    break;
//		                } catch (IOException e) {
//		                	logger.error("I/O error initialising connection thread: " + e.getMessage());
//		                    break;
//		                }
//		            }
//					
//				}
//			}).start();
        	
            while (!Thread.interrupted()) {
                try {
                    // Set up HTTP connection
                    Socket socket = this.httpServerSocket.accept();
                    DefaultHttpServerConnection conn = new DefaultHttpServerConnection();
                    logger.debug("Incoming connection from " + socket.getInetAddress());
                    conn.bind(socket, this.params);

                    // Start worker thread
                    Thread t = new HttpWorkerThread(this.httpService, conn);
                    t.setDaemon(true);
                    t.start();
                } catch (InterruptedIOException ex) {
                    break;
                } catch (IOException e) {
                	logger.error("I/O error initialising connection thread: " + e.getMessage());
                    break;
                }
            }
        }
    }

    static class HttpWorkerThread extends Thread {

        private final HttpService httpservice;
        private final HttpServerConnection conn;

        public HttpWorkerThread(final HttpService httpservice, final HttpServerConnection conn) {
            super();
            this.httpservice = httpservice;
            this.conn = conn;
        }

        public void run() {
        	logger.debug("New HTTP connection thread");
            HttpContext context = new BasicHttpContext(null);
            try {
                while (!Thread.interrupted() && this.conn.isOpen()) {
                    this.httpservice.handleRequest(this.conn, context);
                }
            } catch (ConnectionClosedException ex) {
            	logger.debug("Client closed connection");
            } catch (IOException ex) {
            	logger.debug("I/O error: " + ex.getMessage());
            } catch (HttpException ex) {
            	logger.error("Unrecoverable HTTP protocol violation: " + ex.getMessage());
            } finally {
                try {
                    this.conn.shutdown();
                } catch (IOException ignore) {
                }
            }
        }

    }

}
