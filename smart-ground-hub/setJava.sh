# Set Java 25
$env:JAVA_HOME = "C:\Users\hynre\.jdks\jbr-25.0.2"
$env:Path = "$env:JAVA_HOME\bin;" + $env:Path

# Verify Java version
java -version