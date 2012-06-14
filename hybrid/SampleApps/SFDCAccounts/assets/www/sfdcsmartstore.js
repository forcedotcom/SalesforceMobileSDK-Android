var ACCOUNTS_SOUP_NAME = "ct__accountsSoup";
var OPPORTUNITIES_SOUP_NAME = "ct__opportunitiesSoup";
var INDEXES_ACCOUNTS = [
    {path:"Id", type:"string"},
    {path:"Name", type:"string"},
    {path:"Description", type:"string"},
    {path:"isDirty", type:"string"}
];
var INDEXES_OPPORTUNITIES = [
    {path:"Id", type:"string"},
    {path:"Name", type:"string"},
    {path:"Description", type:"string"},
    {path:"AccountId", type:"string"},
    {path:"CloseDate", type:"string"},
    {path:"StageName", type:"string"},
    {path:"isDirty", type:"string"}
];

function hasSmartstore() {
    if (PhoneGap.hasResource("smartstore") && navigator.smartstore) {
        SFHybridApp.logToConsole("hasSmartstore: " + true);
        return true;
    }
    SFHybridApp.logToConsole("hasSmartstore: " + false);
    return false;
}

function regAccSoup() {
    if (hasSmartstore()) {

        // Registers soup for storing accounts.
        navigator.smartstore.registerSoup(ACCOUNTS_SOUP_NAME,
                INDEXES_ACCOUNTS, regOppSoup, regOppSoup);
    }
}

function regOppSoup() {
    if (hasSmartstore()) {

        // Registers soup for storing opportunities.
        navigator.smartstore.registerSoup(OPPORTUNITIES_SOUP_NAME,
                INDEXES_OPPORTUNITIES, onSuccessRegSoup, onErrorRegSoup);
    }
}

function removeAccSoup() {
    navigator.smartstore.removeSoup(ACCOUNTS_SOUP_NAME, removeOppSoup, removeOppSoup);
}

function removeOppSoup() {
    navigator.smartstore.removeSoup(OPPORTUNITIES_SOUP_NAME, onSuccessRemoveSoup, onErrorRemoveSoup);
}

function addAccounts(entries, success, error) {
    if (hasSmartstore()) {
        navigator.smartstore.upsertSoupEntriesWithExternalId(ACCOUNTS_SOUP_NAME, entries, "Id",
                success, error);
    }
}

function addOpportunities(entries, success, error) {
    if (hasSmartstore()) {
        navigator.smartstore.upsertSoupEntriesWithExternalId(OPPORTUNITIES_SOUP_NAME, entries, "Id",
                success, error);
    }
}

function getAccounts(numAccounts, success, error) {
    if (hasSmartstore()) {
        var querySpec = navigator.smartstore.buildAllQuerySpec("Id", null, numAccounts);
        navigator.smartstore.querySoup(ACCOUNTS_SOUP_NAME, querySpec, function(cursor) {
            success(cursor);
        }, error);
    }
}

function getOpportunities(numOpportunities, success, error) {
    if (hasSmartstore()) {
        var querySpec = navigator.smartstore.buildAllQuerySpec("Id", null, numOpportunities);
        navigator.smartstore.querySoup(OPPORTUNITIES_SOUP_NAME, querySpec, function(cursor) {
            success(cursor);
        }, error);
    }
}

function getAccById(id, success, error) {
    if (hasSmartstore()) {
        var querySpec = navigator.smartstore.buildExactQuerySpec("Id", id, 1);
        navigator.smartstore.querySoup(ACCOUNTS_SOUP_NAME, querySpec, function(cursor) {
            success(cursor);
        }, error);
    }
}

function getOppById(id, success, error) {
    if (hasSmartstore()) {
        var querySpec = navigator.smartstore.buildExactQuerySpec("Id", id, 1);
        navigator.smartstore.querySoup(OPPORTUNITIES_SOUP_NAME, querySpec, function(cursor) {
            success(cursor);
        }, error);
    }
}

function getNumAccounts(success, error) {
    if (hasSmartstore()) {
        var querySpec = navigator.smartstore.buildAllQuerySpec("Id", null, 1);
        navigator.smartstore.querySoup(ACCOUNTS_SOUP_NAME, querySpec, function(cursor) {
            success(cursor);
        }, error);
    }
}

function getNumOpportunities(success, error) {
    if (hasSmartstore()) {
        var querySpec = navigator.smartstore.buildAllQuerySpec("Id", null, 1);
        navigator.smartstore.querySoup(OPPORTUNITIES_SOUP_NAME, querySpec, function(cursor) {
            success(cursor);
        }, error);
    }
}

function updateAccount(id, newName, newDesc, success, error) {
    var fields = {};
    if (newName != null) {
        fields.Name = newName;
    }
    if (newDesc != null) {
        fields.Description = newDesc;
    }
    forcetkClient.upsert("Account", "Id", id, fields, success, error);
}

function updateOpportunity(id, newName, newDesc, newAccountId, newCloseDate, newStageName, success, error) {
    var fields = {};
    if (newName != null) {
        fields.Name = newName;
    }
    if (newDesc != null) {
        fields.Description = newDesc;
    }
    if (newCloseDate != null) {
        fields.AccountId = newCloseDate;
    }
    if (newCloseDate != null) {
        fields.CloseDate = newCloseDate;
    }
    if (newStageName != null) {
        fields.StageName = newStageName;
    }
    forcetkClient.upsert("Opportunity", "Id", id, fields, success, error);
}

function fetchDirtyAccounts(success, error) {
    if (hasSmartstore()) {
        var querySpec = navigator.smartstore.buildExactQuerySpec("isDirty", "true", 1);
        navigator.smartstore.querySoup(ACCOUNTS_SOUP_NAME, querySpec, function(response) {
            var allQuerySpec = navigator.smartstore.buildExactQuerySpec("isDirty", "true", response.totalPages - 1);
            navigator.smartstore.querySoup(ACCOUNTS_SOUP_NAME, allQuerySpec, function(cursor) {
                success(cursor);
            }, error);
        }, error);
    }
}

function fetchDirtyOpportunities(success, error) {
    if (hasSmartstore()) {
        var querySpec = navigator.smartstore.buildExactQuerySpec("isDirty", "true", 1);
        navigator.smartstore.querySoup(OPPORTUNITIES_SOUP_NAME, querySpec, function(response) {
            var allQuerySpec = navigator.smartstore.buildExactQuerySpec("isDirty", "true", response.totalPages - 1);
            navigator.smartstore.querySoup(OPPORTUNITIES_SOUP_NAME, allQuerySpec, function(cursor) {
                success(cursor);
            }, error);
        }, error);
    }
}
