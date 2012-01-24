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
 * A test suite for SmartStore
 * This file assumes that qunit.js has been previously loaded, as well as SFHybridApp.js and SFTestSuite.js
 * To display results you'll need to load qunit.css and SFHybridApp.css as well.
 */
if (typeof SmartStoreTestSuite === 'undefined') { 

/**
 * Constructor for SmartStoreTestSuite
 */
var SmartStoreTestSuite = function () {
	SFTestSuite.call(this);

	this.module = "smartstore";
	this.defaultSoupName = "myPeopleSoup";
	this.defaultSoupIndexes = [{path:"Name", type:"string"}, {path:"Id", type:"string"}];
	this.NUM_CURSOR_MANIPULATION_ENTRIES = 103;
};

// We are sub-classing SFTestSuite
SmartStoreTestSuite.prototype = new SFTestSuite;
SmartStoreTestSuite.prototype.constructor = SmartStoreTestSuite;

/*
 * For each test, we first remove and re-add the default soup
 */
SmartStoreTestSuite.prototype.runTest= function (methName) {
	SFHybridApp.logToConsole("In runTest: methName=" + methName);
	var self = this;
	
	self.removeDefaultSoup(function() {
		SFHybridApp.logToConsole("In Here 1" + new Date());
		self.registerDefaultSoup(function() {
			SFHybridApp.logToConsole("In Here 2" + new Date());
			self[methName]();
		});
	});
};

/**
 * Helper method that creates default soup
 */
SmartStoreTestSuite.prototype.registerDefaultSoup = function(callback) {
	SFHybridApp.logToConsole("In registerDefaultSoup");
	
	var self = this;
    navigator.smartstore.registerSoup(self.defaultSoupName, self.defaultSoupIndexes, 
		function(soup) { 
			SFHybridApp.logToConsole("registerSoup succeeded");
			if (callback !== null) callback(soup);
		}, 
		function(param) { self.setTestFailed("registerSoup failed: " + param); }
      );
};

/**
 * Helper method that drops default soup
 */
SmartStoreTestSuite.prototype.removeDefaultSoup = function(callback) {
	SFHybridApp.logToConsole("In removeDefaultSoup");
	
	var self = this;
    navigator.smartstore.removeSoup(self.defaultSoupName,  
		function() { 
			SFHybridApp.logToConsole("removeSoup succeeded" + new Date());
			if (callback !== null) callback();
		}, 
		function(param) {
			SFHybridApp.logToConsole("removeSoup failed" + new Date());
			self.setTestFailed("removeSoup failed: " + param); }
      );
};

/**
 * Helper method that adds three soup entries to default soup
 */
SmartStoreTestSuite.prototype.stuffTestSoup = function(callback) {
	SFHybridApp.logToConsole("In stuffTestSoup");
	
	var myEntry1 = { Name: "Todd Stellanova", Id: "00300A",  attributes:{type:"Contact"} };
    var myEntry2 = { Name: "Pro Bono Bonobo",  Id: "00300B", attributes:{type:"Contact"}  };
    var myEntry3 = { Name: "Robot", Id: "00300C", attributes:{type:"Contact"}  };
    var entries = [myEntry1, myEntry2, myEntry3];

	this.addEntriesToTestSoup(entries, callback);
};

/**
 * Helper method that adds n soup entries to default soup
 */
SmartStoreTestSuite.prototype.addGeneratedEntriesToTestSoup = function(nEntries, callback) {
	SFHybridApp.logToConsole("In addGeneratedEntriesToTestSoup: nEntries=" + nEntries);
 
	var entries = [];
	for (var i = 0; i < nEntries; i++) {
		var myEntry = { Name: "Todd Stellanova" + i, Id: "00300" + i,  attributes:{type:"Contact"} };
		entries.push(myEntry);
	}
	
	this.addEntriesToTestSoup(entries, callback);
	
};

/**
 * Helper method that adds soup entries to default soup
 */
SmartStoreTestSuite.prototype.addEntriesToTestSoup = function(entries, callback) {
	SFHybridApp.logToConsole("In addEntriesToTestSoup: entries.length=" + entries.length);

	var self = this;
    navigator.smartstore.upsertSoupEntries(self.defaultSoupName, entries, 
		function(upsertedEntries) {
		    SFHybridApp.logToConsole("addEntriesToTestSoup of " + upsertedEntries.length + " entries succeeded");
			callback(upsertedEntries);
		}, 
		function(param) { self.setTestFailed("upsertSoupEntries failed: " + param); }
	);
};

/** 
 * TEST upsertSoupEntries
 */
SmartStoreTestSuite.prototype.testUpsertSoupEntries = function()  {
	SFHybridApp.logToConsole("In testUpsertSoupEntries");

	var self = this;
	self.addGeneratedEntriesToTestSoup(7, function(entries) {
		QUnit.equal(entries.length, 7);
		
		//upsert another batch
		self.addGeneratedEntriesToTestSoup(12, function(entries) {
			QUnit.equal(entries.length, 12);
			self.setTestSuccess();
		});
	});
}; 

/**
 * TEST retrieveSoupEntries
 */
SmartStoreTestSuite.prototype.testRetrieveSoupEntries = function()  {
	SFHybridApp.logToConsole("In testRetrieveSoupEntries");
	
	var self = this; 
	self.stuffTestSoup(function(entries) {
		QUnit.equal(entries.length, 3);
		var soupEntry0Id = entries[0]._soupEntryId;
		var soupEntry2Id = entries[2]._soupEntryId;
		
		navigator.smartstore.retrieveSoupEntries(self.defaultSoupName, [soupEntry2Id, soupEntry0Id], 
			function(retrievedEntries) {
			    QUnit.equal(retrievedEntries.length, 2);
				QUnit.equal(soupEntry2Id, retrievedEntries[0]._soupEntryId);
				QUnit.equal(soupEntry0Id, retrievedEntries[1]._soupEntryId);
				self.setTestSuccess();
			}, 
			function(param) { self.setTestFailed("retrieveSoupEntries failed: " + param); }
		);
	});
};


/**
 * TEST removeFromSoup
 */
SmartStoreTestSuite.prototype.testRemoveFromSoup = function()  {
	SFHybridApp.logToConsole("In testRemoveFromSoup");	
	
	var self = this; 
	self.stuffTestSoup(function(entries) {
		var soupEntryIds = [];
		QUnit.equal(entries.length, 3);
		
		for (var i = entries.length - 1; i >= 0; i--) {
			var entry = entries[i];
			soupEntryIds.push(entry._soupEntryId);
		}
		
		navigator.smartstore.removeFromSoup(self.defaultSoupName, soupEntryIds, 
			function(param) {
				QUnit.equal(status, "OK", "removeFromSoup OK");
				
				var querySpec = new SoupQuerySpec("Name", null);
				navigator.smartstore.querySoup(self.defaultSoupName, querySpec, 
					function(cursor) {
						var nEntries = cursor.currentPageOrderedEntries.length;
						QUnit.equal(nEntries, 0, "currentPageOrderedEntries correct");
						self.setTestSuccess();
					}, 
					function(param) { self.setTestFailed("querySoup: " + param); }
				);
			}, 
			function(param) { self.setTestFailed("removeFromSoup: " + param); }
		);
	});
};

/* 
TEST querySoup
*/
SmartStoreTestSuite.prototype.testQuerySoup = function()  {
	SFHybridApp.logToConsole("In testQuerySoup");	
	
	var self = this;
	self.stuffTestSoup(function(entries) {
		QUnit.equal(entries.length, 3);
		
	    var querySpec = new SoupQuerySpec("Name", "Robot");
	    querySpec.pageSize = 25;
	    navigator.smartstore.querySoup(self.defaultSoupName, querySpec, 
			function(cursor) {
				QUnit.equal(cursor.totalPages, 1, "totalPages correct");
				var nEntries = cursor.currentPageOrderedEntries.length;
				QUnit.equal(nEntries, 1, "currentPageOrderedEntries correct");
				self.setTestSuccess("testQuerySoup");
			}, 
			function(param) { self.setTestFailed("querySoup: " + param); }
	    );
	});
};



/**
 * TEST testManipulateCursor
 */
SmartStoreTestSuite.prototype.testManipulateCursor = function()  {
	SFHybridApp.logToConsole("In testManipulateCursor");	
	
	var self = this;
	this.addGeneratedEntriesToTestSoup(self.NUM_CURSOR_MANIPULATION_ENTRIES, function(entries) {

		QUnit.equal(entries.length, self.NUM_CURSOR_MANIPULATION_ENTRIES);
	    var querySpec = new SoupQuerySpec("Name", null);
	
	    navigator.smartstore.querySoup(self.defaultSoupName, querySpec, 
			function(cursor) {
				QUnit.equal(cursor.currentPageIndex, 0, "currentPageIndex correct");
				QUnit.equal(cursor.pageSize, 10, "pageSize correct");
				
				var nEntries = cursor.currentPageOrderedEntries.length;
				QUnit.equal(nEntries, cursor.pageSize, "nEntries matches pageSize");
							
				self.forwardCursorToEnd(cursor);
			}, 
			function(param) { self.setTestFailed("querySoup: " + param); }
		);
	});
};

/**
 * Page through the cursor til we reach the end.
 * Used by testManipulateCursor
 */
SmartStoreTestSuite.prototype.forwardCursorToEnd = function(cursor) {
	SFHybridApp.logToConsole("In forwardCursorToEnd");	
	
	var self = this;
	
	navigator.smartstore.moveCursorToNextPage(cursor, 
		function(nextCursor) {
			var pageCount = nextCursor.currentPageIndex + 1;
			var nEntries = nextCursor.currentPageOrderedEntries.length;
			
			if (pageCount < nextCursor.totalPages) {
				SFHybridApp.logToConsole("pageCount:" + pageCount + " of " + nextCursor.totalPages);
				QUnit.equal(nEntries, nextCursor.pageSize, "nEntries matches pageSize [" + nextCursor.currentPageIndex + "]" );
				
				self.forwardCursorToEnd(nextCursor);
			} 
			else {
				var expectedCurEntries = nextCursor.pageSize;
				var remainder = self.NUM_CURSOR_MANIPULATION_ENTRIES % nextCursor.pageSize;
				if (remainder > 0) {
					expectedCurEntries = remainder;
					SFHybridApp.logToConsole("remainder: " + remainder);
				}
				
				QUnit.equal(nextCursor.currentPageIndex, nextCursor.totalPages-1, "final pageIndex correct");
				QUnit.equal(nEntries, expectedCurEntries, "last page nEntries matches");
				
				self.setTestSuccess();
			}
		}, 
		function(param) { self.setTestFailed("moveCursorToNextPage: " + param); }
	);
};

}

