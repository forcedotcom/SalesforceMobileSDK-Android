/*
 * Copyright (c) 2012, salesforce.com, inc.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are permitted provided
 * that the following conditions are met:
 *
 * Redistributions of source code must retain the above copyright notice, this list of conditions and the
 * following disclaimer.
 *
 * Redistributions in binary form must reproduce the above copyright notice, this list of conditions and
 * the following disclaimer in the documentation and/or other materials provided with the distribution.
 *
 * Neither the name of salesforce.com, inc. nor the names of its contributors may be used to endorse or
 * promote products derived from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED
 * WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A
 * PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED
 * TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION)
 * HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */

/**
 * Abstract test suite
 * This file assumes that qunit.js has been previously loaded, as well as SFHybridApp.js.
 * To display results you'll need to load qunit.css and SFHybridApp.css as well.
 */
if (typeof SFTestSuite === 'undefined') {

/**
 * Constructor
 */
var SFTestSuite = function () {
	this.module = "SFTestSuite"; // should be overridden in subclasses
	this.allTests = [];
	this.stateOfTestByName = {};
	this.currentTestName = null;
	
    this.numTestsFinished = 0;
    this.numFailedTests = 0;
    this.numPassedTests = 0;
    
	this.IDLE_TEST_STATE = 'idle';
	this.RUNNING_TEST_STATE = 'running';
	this.FAIL_TEST_STATE = 'fail';
	this.SUCCESS_TEST_STATE = 'success';
};


/**
 * Method to run all the tests
 */
SFTestSuite.prototype.startTests = function() {
	SFHybridApp.logToConsole("In startTests");
	var self = this;

	//collect a list of test methods by introspection
	for (var key in self) {
		//we specifically don't check hasOwnProperty here, to grab proto methods
		var val = self[key];
		if (typeof val === 'function') {
			if (key.indexOf("test") === 0) {
				self.allTests.push(key);
				self.stateOfTestByName[key] = self.IDLE_TEST_STATE;
			}
		}
	}
	
	QUnit.init();
	QUnit.stop();//don't start running tests til they're all queued
	QUnit.module(self.module);
	
	self.allTests.forEach(function(methName){
		SFHybridApp.logToConsole("Queueing: " + methName);
		QUnit.asyncTest(methName, function() {
			self.preRun(methName);
			self.runTest(methName);
		});
	});
	QUnit.start();//start qunit now that all tests are queued
	
};

/**
 * Method to run a single test
 */
SFTestSuite.prototype.startTest = function(methName) {
	SFHybridApp.logToConsole("In startTest: methName=" + methName);	
	var self = this;
	
	self.allTests.push(methName);
	self.stateOfTestByName[methName] = self.IDLE_TEST_STATE;

	QUnit.init();
	QUnit.stop();//don't start running tests til they're all queued
	QUnit.module(this.module);
	QUnit.test(methName, function() {
		self.preRun(methName);
		self.runTest(methName);
	});
	QUnit.start();//start qunit now that all tests are queued
};

/**
 * Method run before running a test
 */
SFTestSuite.prototype.preRun = function(methName) {
	SFHybridApp.logToConsole("In preRun: methName=" + methName);
	this.currentTestName = methName;
	this.stateOfTestByName[methName] = self.RUNNING_TEST_STATE;
}

/**
 * Method to run an actual test
 * Sub-classes should override this method if they need anything to be setup before running tests
 */
SFTestSuite.prototype.runTest= function (methName) {
	SFHybridApp.logToConsole("In runTest: methName=" + methName);
	this[methName]();
};

/**
 * Method called to report that the current test failed
 */
SFTestSuite.prototype.setTestFailed = function(error) {
	SFHybridApp.logToConsole("In setTestFailedByName: currentTestName=" + this.currentTestName + " , error=" + error);

	// update stats
	this.stateOfTestByName[this.currentTestName] = this.FAIL_TEST_STATE;    
    this.numTestsFinished++;
    this.numFailedTests++;

    // let test runner know
    if (navigator.testrunner) navigator.testrunner.onTestComplete(this.currentTestName, false, error);
    
	// inform qunit that this test failed and unpause qunit
	QUnit.ok(false, this.currentTestName);
	QUnit.start();
};

/**
 * Method called to report that the current test succeeded
 */
SFTestSuite.prototype.setTestSuccess = function() {
	SFHybridApp.logToConsole("In setTestSuccessByName: currentTestName=" + this.currentTestName);

	// update stats
	this.stateOfTestByName[this.currentTestName] = this.SUCCESS_TEST_STATE;
    this.numTestsFinished++;
    this.numPassedTests++;

    // let test runner know	
    if (navigator.testrunner) navigator.testrunner.onTestComplete(this.currentTestName, true, "");
	
	// inform qunit that this test passed and unpause qunit
	QUnit.ok(true, this.currentTestName);
	QUnit.start();
};

}