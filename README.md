# OPC UA Address Space browser

### Usage
Build executable jar and run it:
```
mvn package
java -jar target/opcua-as-browser-1.0-jar-with-dependencies.jar
```
### Options and parameters
```
-d,--depth <arg>    Depth of browsing.
-f,--file <arg>     Redirect output to file.
-h,--help           Print this message.
-r,--root <arg>     Root node in format "namespaceIndex,identifier"
-s,--server <arg>   Server url, default is
                    opc.tcp://milo.digitalpetri.com:62541/milo.
-t,--time           Print total time spent.
-v,--verbose        Print both id and name.
```