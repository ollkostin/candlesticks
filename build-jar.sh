echo "clean and build jar"
./gradlew clean build jar
echo "run partner-service with docker"
docker-compose up -d
echo "run candlesticks"
./gradlew run
