module.exports = {
  presets: ['module:@react-native/babel-preset'],
  plugins: [
    ["@babel/plugin-transform-class-static-block", { "loose": true }],
  	["@babel/plugin-transform-private-methods", { "loose": true }]
  ]
};
