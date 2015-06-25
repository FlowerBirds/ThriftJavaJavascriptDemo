package thrift;

import org.apache.thrift.protocol.TJSONProtocol;
import org.apache.thrift.server.TServlet;

import tutorial.Calculator;

public class CalculatorServlet extends TServlet 
{
   public CalculatorServlet()  
   {
      //'Calculator' is the generated class from thrift, 
      //'CalculatorHandler' is the implement class for thrift rpc
      super(new Calculator.Processor(
            new CalculatorHandler()),
            new TJSONProtocol.Factory());
  }
}