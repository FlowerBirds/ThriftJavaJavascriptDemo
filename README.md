# Thrift Java server Javascript client demo
A sample project demonstrating how to get Thrift going with a Java server and a Javascript client

Because it took me half a day to get this going, I thought I'd share my findings.

### Requirements

(Maven)[https://maven.apache.org/download.cgi]
(Thrift binary)[http://www.apache.org/dyn/closer.cgi?path=/thrift/0.9.2/thrift-0.9.2.exe]
(This sample project)[https://github.com/LukeOwncloud/ThriftJavaJavascriptDemo/archive/master.zip]

### How to

* Copy `thrift-0.9.2.exe` to `ThriftJavaJavascriptDemo-master`
* Start Thrift compiler and Java compiler by executing `make.bat`
* Run `run-server.bat`
* Run `run-clients.bat`. This opens a browser (-> JS client) and at the same time a Java client.
* Open project with Eclipse to have a closer look.

Yes, the code can be cleaned up. But it works.

### Source

Most files come from some part of the sources of Thrift. For example:

(shared.thrift)[https://git-wip-us.apache.org/repos/asf?p=thrift.git;a=blob_plain;f=tutorial/shared.thrift;hb=HEAD]
(tutorial.thrift)[https://git-wip-us.apache.org/repos/asf?p=thrift.git;a=blob_plain;f=tutorial/tutorial.thrift;hb=HEAD]
(index.html)[https://git-wip-us.apache.org/repos/asf?p=thrift.git;a=blob;f=tutorial/js/tutorial.html;h=d7f3945f2edb412e85579317817d5b10d6f2b72d;hb=HEAD]
(Httpd.java)[https://git-wip-us.apache.org/repos/asf?p=thrift.git;a=blob_plain;f=tutorial/js/src/Httpd.java;hb=HEAD]
etc.