thrift-0.9.2.exe -gen js shared.thrift
thrift-0.9.2.exe -gen js tutorial.thrift
thrift-0.9.2.exe -gen java -out src\main\java shared.thrift
thrift-0.9.2.exe -gen java -out src\main\java tutorial.thrift
mvn compile
pause