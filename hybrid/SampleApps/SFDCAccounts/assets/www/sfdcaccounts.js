var numAccounts = 0;
var numOpportunities = 0;

function regLinkClickHandlers() {
    var $j = jQuery.noConflict();
    $j('#link_fetch_device_accounts').click(function() {
        SFHybridApp.logToConsole("link_fetch_device_accounts clicked");
        getNumAccounts(onSuccessNumAccounts, onErrorDevice);
    });

    $j('#link_fetch_device_opportunities').click(function() {
        SFHybridApp.logToConsole("link_fetch_device_opportunities clicked");
        getNumOpportunities(onSuccessNumOpportunities, onErrorDevice);
    });

    $j('#link_fetch_sfdc_opportunities').click(function() {
        SFHybridApp.logToConsole("link_fetch_sfdc_opportunities clicked");
        forcetkClient.query("SELECT Id, Name, Description, AccountId, CloseDate, StageName FROM Opportunity",
                onSuccessSfdcOpportunities, onErrorSfdc);
    });

    $j('#link_fetch_sfdc_accounts').click(function() {
        SFHybridApp.logToConsole("link_fetch_sfdc_accounts clicked");
        forcetkClient.query("SELECT Id, Name, Description FROM Account", onSuccessSfdcAccounts, onErrorSfdc);
    });

    $j('#link_sync_accounts').click(function() {
        SFHybridApp.logToConsole("link_sync_accounts clicked");
        syncAccounts();
    });

    $j('#link_sync_opportunities').click(function() {
        SFHybridApp.logToConsole("link_sync_opportunities clicked");
        syncOpportunities();
    });

    $j('#link_reset').click(function() {
        SFHybridApp.logToConsole("link_reset clicked");
        resetDisplay();
        removeAccSoup();
    });

    $j('#link_logout').click(function() {
        SFHybridApp.logToConsole("link_logout clicked");
        SalesforceOAuthPlugin.logout();
    });
}

function regAccClickHandlers() {
    var $j = jQuery.noConflict();
    $j('#save_acc').click(function() {
        SFHybridApp.logToConsole("save_acc clicked");
        var id = $j("#div_account_editor input:text[name=id]").val();
        var name = $j("#div_account_editor input:text[name=name]").val();
        var description = $j("#div_account_editor input:text[name=description]").val();
        var elem = {Id: id, Name: name, Description: description, isDirty: "true"};
        resetDisplay();
        addAccounts([elem], onSaveSuccess, onSaveError);
    });

    $j('#cancel_acc').click(function() {
        SFHybridApp.logToConsole("cancel_acc clicked");
        resetDisplay();
    });
}

function regOppClickHandlers() {
    var $j = jQuery.noConflict();
    $j('#save_opp').click(function() {
        SFHybridApp.logToConsole("save_opp clicked");
        var id = $j("#div_opportunity_editor input:text[name=id]").val();
        var name = $j("#div_opportunity_editor input:text[name=name]").val();
        var description = $j("#div_opportunity_editor input:text[name=description]").val();
        var accountid = $j("#div_opportunity_editor input:text[name=accountid]").val();
        var closedate = $j("#div_opportunity_editor input:text[name=closedate]").val();
        var stagename = $j("#div_opportunity_editor input:text[name=stagename]").val();
        var elem = {Id: id, Name: name, Description: description,
                AccountId: accountid, CloseDate: closedate,
                StageName: stagename, isDirty: "true"};
        resetDisplay();
        addOpportunities([elem], onSaveSuccess, onSaveError);
    });

    $j('#cancel_opp').click(function() {
        SFHybridApp.logToConsole("cancel_opp clicked");
        resetDisplay();
    });
}

function resetDisplay() {
    $j("#div_device_account_list").html("")
    $j("#div_device_opportunity_list").html("")
    $j("#div_sfdc_opportunity_list").html("")
    $j("#div_sfdc_account_list").html("")
    $j("#div_account_editor").html("")
    $j("#div_opportunity_editor").html("")
    $j("#console").html("")
}

function onSuccessNumAccounts(response) {
    numAccounts = response.totalPages - 1;
    getAccounts(numAccounts, onSuccessDeviceAccounts, onErrorDevice);
}

function onSuccessNumOpportunities(cursor) {
    numOpportunities = cursor.totalPages - 1;
    getOpportunities(numOpportunities, onSuccessDeviceOpportunities, onErrorDevice);
}

function onSuccessRemoveSoup() {
    SFHybridApp.logToConsole("Successfully Removed Soup!");
    regAccSoup();
}

function onErrorRemoveSoup(error) {
    SFHybridApp.logToConsole("Failed to Remove Soup: " + JSON.stringify(error));
}

function onErrorDevice(error) {
    SFHybridApp.logToConsole("onErrorDevice: " + JSON.stringify(error));
    alert('Error getting device data!');
}

function onSuccessDeviceAccounts(response) {
    resetDisplay();
    var $j = jQuery.noConflict();
    SFHybridApp.logToConsole("onSuccessDeviceAccounts: received " + numAccounts + " accounts");
    $j("#div_device_account_list").html("")
    var ul = $j('<ul id="accList" data-role="listview" data-inset="true" data-theme="a" data-dividertheme="a"></ul>');
    $j("#div_device_account_list").append(ul);
    ul.append($j('<li data-role="list-divider">Device Accounts: ' + numAccounts + '</li>'));
    $j.each(response.currentPageOrderedEntries, function(i, currentPageOrderedEntries) {
        if (currentPageOrderedEntries.Description === undefined) {
            currentPageOrderedEntries.Description = "";
        }
        var newLi = $j("<li id='" + currentPageOrderedEntries.Id + "'><a href='#'>" + (i + 1) + " - " + currentPageOrderedEntries.Id + " - "
                + currentPageOrderedEntries.Name + " - " + currentPageOrderedEntries.Description
                + "</a></li>");
        ul.append(newLi);
    });

    // Handles click events for each list item.
    ul.delegate("li", "click", function(e) {
        SFHybridApp.logToConsole("Item Clicked: " + this.id);
        resetDisplay();
        $j('#div_account_editor').load("edit_account.html");
        getAccById(this.id, function(response) {
            $j("#div_account_editor input:text[name=id]").val(response.currentPageOrderedEntries[0].Id);
            $j("#div_account_editor input:text[name=name]").val(response.currentPageOrderedEntries[0].Name);
            $j("#div_account_editor input:text[name=description]").val(response.currentPageOrderedEntries[0].Description);
        }, onErrorDevice);
    });
    $j("#div_device_account_list").trigger("create")
}

function onSuccessDeviceOpportunities(response) {
    resetDisplay();
    var $j = jQuery.noConflict();
    SFHybridApp.logToConsole("onSuccessDeviceOpportunities: received " + numOpportunities + " opportunities");
    $j("#div_device_opportunity_list").html("")
    var ul = $j('<ul id="oppList" data-role="listview" data-inset="true" data-theme="a" data-dividertheme="a"></ul>');
    $j("#div_device_opportunity_list").append(ul);
    ul.append($j('<li data-role="list-divider">Salesforce Opportunities: ' + numOpportunities + '</li>'));
    $j.each(response.currentPageOrderedEntries, function(i, currentPageOrderedEntries) {
        if (currentPageOrderedEntries.Description === undefined) {
            currentPageOrderedEntries.Description = "";
        }
        var newLi = $j("<li id='" + currentPageOrderedEntries.Id + "'><a href='#'>" + (i + 1) + " - " + currentPageOrderedEntries.Id + " - "
                + currentPageOrderedEntries.Name + " - " + currentPageOrderedEntries.Description
                + "</a></li>");
        ul.append(newLi);
    });

    // Handles click events for each list item.
    ul.delegate("li", "click", function(e) {
        SFHybridApp.logToConsole("Item Clicked: " + this.id);
        resetDisplay();
        $j('#div_opportunity_editor').load("edit_opportunity.html");
        getOppById(this.id, function(response) {
            $j("#div_opportunity_editor input:text[name=id]").val(response.currentPageOrderedEntries[0].Id);
            $j("#div_opportunity_editor input:text[name=name]").val(response.currentPageOrderedEntries[0].Name);
            $j("#div_opportunity_editor input:text[name=description]").val(response.currentPageOrderedEntries[0].Description);
            $j("#div_opportunity_editor input:text[name=accountid]").val(response.currentPageOrderedEntries[0].AccountId);
            $j("#div_opportunity_editor input:text[name=closedate]").val(response.currentPageOrderedEntries[0].CloseDate);
            $j("#div_opportunity_editor input:text[name=stagename]").val(response.currentPageOrderedEntries[0].StageName);
        }, onErrorDevice);
    });
    $j("#div_device_opportunity_list").trigger("create")
}

function onSuccessSfdcAccounts(response) {
    resetDisplay();
    var $j = jQuery.noConflict();
    SFHybridApp.logToConsole("onSuccessSfdcAccounts: received " + response.totalSize + " accounts");
    $j("#div_sfdc_account_list").html("")
    var ul = $j('<ul id="accList" data-role="listview" data-inset="true" data-theme="a" data-dividertheme="a"></ul>');
    $j("#div_sfdc_account_list").append(ul);
    ul.append($j('<li data-role="list-divider">Salesforce Accounts: ' + response.totalSize + '</li>'));
    var acc = [];
    $j.each(response.records, function(i, record) {

        // Smartstore doesn't accept null values.
        if (record.Description === null) {
            record.Description = "";
        }
        var elem = {Id: record.Id, Name: record.Name, Description: record.Description, isDirty: "false"};
        acc.push(elem);
        var newLi = $j("<li id='" + record.Id + "'><a href='#'>" + (i + 1) + " - " + record.Id + " - "
                + record.Name + " - " + record.Description +
                "</a></li>");
        ul.append(newLi);
    });

    // Handles click events for each list item.
    ul.delegate("li", "click", function(e) {
        SFHybridApp.logToConsole("Item Clicked: " + this.id);
        resetDisplay();
        $j('#div_account_editor').load("edit_account.html");
        getAccById(this.id, function(response) {
            $j("#div_account_editor input:text[name=id]").val(response.currentPageOrderedEntries[0].Id);
            $j("#div_account_editor input:text[name=name]").val(response.currentPageOrderedEntries[0].Name);
            $j("#div_account_editor input:text[name=description]").val(response.currentPageOrderedEntries[0].Description);
        }, onErrorDevice);
    });

    // Caches data locally, by putting it in the Smartstore.
    addAccounts(acc, onCachingSuccess, onCachingError);
    $j("#div_sfdc_account_list").trigger("create")
}

function onSuccessSfdcOpportunities(response) {
    resetDisplay();
    var $j = jQuery.noConflict();
    SFHybridApp.logToConsole("onSuccessSfdcOpportunities: received " + response.totalSize + " opportunities");
    $j("#div_sfdc_opportunity_list").html("")
    var ul = $j('<ul id="oppList" data-role="listview" data-inset="true" data-theme="a" data-dividertheme="a"></ul>');
    $j("#div_sfdc_opportunity_list").append(ul);
    ul.append($j('<li data-role="list-divider">Salesforce Opportunities: ' + response.totalSize + '</li>'));
    var opp = [];
    $j.each(response.records, function(i, record) {

        // Smartstore doesn't accept null values.
        if (record.Description === null) {
            record.Description = "";
        }
        var elem = {Id: record.Id, Name: record.Name, Description: record.Description,
                AccountId: record.AccountId, CloseDate: record.CloseDate,
                StageName: record.StageName, isDirty: "false"};
        opp.push(elem);
        var newLi = $j("<li id='" + record.Id + "'><a href='#'>" + (i + 1) + " - " + record.Id + " - "
                + record.Name + " - " + record.Description +
                "</a></li>");
        ul.append(newLi);
    });

    // Handles click events for each list item.
    ul.delegate("li", "click", function(e) {
        SFHybridApp.logToConsole("Item Clicked: " + this.id);
        resetDisplay();
        $j('#div_opportunity_editor').load("edit_opportunity.html");
        getOppById(this.id, function(response) {
            $j("#div_opportunity_editor input:text[name=id]").val(response.currentPageOrderedEntries[0].Id);
            $j("#div_opportunity_editor input:text[name=name]").val(response.currentPageOrderedEntries[0].Name);
            $j("#div_opportunity_editor input:text[name=description]").val(response.currentPageOrderedEntries[0].Description);
            $j("#div_opportunity_editor input:text[name=accountid]").val(response.currentPageOrderedEntries[0].AccountId);
            $j("#div_opportunity_editor input:text[name=closedate]").val(response.currentPageOrderedEntries[0].CloseDate);
            $j("#div_opportunity_editor input:text[name=stagename]").val(response.currentPageOrderedEntries[0].StageName);
        }, onErrorDevice);
    });

    // Caches data locally, by putting it in the Smartstore.
    addOpportunities(opp, onCachingSuccess, onCachingError);
    $j("#div_sfdc_opportunity_list").trigger("create")
}

function onSaveError(error) {
    SFHybridApp.logToConsole("Error While Saving!" + JSON.stringify(error));
    alert('Error While Saving!');
}

function onSaveSuccess() {
    SFHybridApp.logToConsole("Save Successful!");
    alert('Save Successful!');
}

function onCachingSuccess() {
    SFHybridApp.logToConsole("Successully Inserted into Smartstore!");
}

function onCachingError(error) {
    SFHybridApp.logToConsole("Error While Inserting into Smartstore!" + JSON.stringify(error));
}

function onErrorSfdc(error) {
    SFHybridApp.logToConsole("onErrorSfdc: " + JSON.stringify(error));
    alert('Error getting SFDC data!');
}

function syncAccounts() {
    fetchDirtyAccounts(function(response) {
        $j.each(response.currentPageOrderedEntries, function(i, currentPageOrderedEntries) {
            updateAccount(currentPageOrderedEntries.Id, currentPageOrderedEntries.Name,
                    currentPageOrderedEntries.Description, function(response) {
                    SFHybridApp.logToConsole("Successfully Synced Account!");
                }, onErrorSync);
        });
        onSuccessSync();
        forcetkClient.query("SELECT Id, Name, Description FROM Account", onSuccessSfdcAccounts, onErrorSfdc);
    }, onErrorSync);
}

function syncOpportunities() {
    fetchDirtyOpportunities(function(response) {
        $j.each(response.currentPageOrderedEntries, function(i, currentPageOrderedEntries) {
            updateOpportunity(currentPageOrderedEntries.Id, currentPageOrderedEntries.Name,
                    currentPageOrderedEntries.Description, currentPageOrderedEntries.AccountId,
                    currentPageOrderedEntries.CloseDate, currentPageOrderedEntries.StageName, function(response) {
                SFHybridApp.logToConsole("Successfully Synced Opportunity!");
            }, onErrorSync);
        });
        onSuccessSync();
        forcetkClient.query("SELECT Id, Name, Description, AccountId, CloseDate, StageName FROM Opportunity",
                onSuccessSfdcOpportunities, onErrorSfdc);
    }, onErrorSync);
}

function onSuccessSync() {
    SFHybridApp.logToConsole("onSuccessSync called!");
    alert('Sync Completed Successfully!');
}

function onErrorSync(error) {
    SFHybridApp.logToConsole("onErrorSync: " + JSON.stringify(error));
}
