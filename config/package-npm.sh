npmLocation=`which npm`
echo "npm location: $npmLocation"
nodeLocation=`which node`
echo "node location: $nodeLocation"
npm -version
node --version
ant -buildfile build_npm.xml
