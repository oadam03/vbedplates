#setting forge for my java 8
$env:JAVA_HOME = "C:\Users\adamj\.jdks\corretto-1.8.0_472"
$env:PATH = "$env:JAVA_HOME\bin;" + $env:PATH
.\gradlew @args