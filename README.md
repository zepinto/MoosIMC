# MoosIMC
Simple Java connector for exposing MOOS vehicles to IMC

# Compiling
Use Apache ANT for generating an executable jar:

```ant```

# Running
```java -jar MoosAdapter.jar <vehicle> <imc_id> <moos_hostname> <moos_port>```

Example:
```java -jar MoosAdapter.jar caravela 0x0802 localhost 9000```
