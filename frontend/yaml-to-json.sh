#!/usr/bin/env node

var YAML = require('js-yaml')
var fs = require("fs")

yaml = fs.readFileSync('/dev/stdin').toString()
json = JSON.stringify(YAML.load(yaml))

console.log(json)