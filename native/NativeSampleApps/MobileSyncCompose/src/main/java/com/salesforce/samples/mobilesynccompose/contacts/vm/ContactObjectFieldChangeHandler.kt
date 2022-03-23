package com.salesforce.samples.mobilesynccompose.contacts.vm

import com.salesforce.samples.mobilesynccompose.core.salesforceobject.SObjectId

interface ContactObjectFieldChangeHandler {
    fun onFirstNameChange(id: SObjectId, newFirstName: String?)
    fun onLastNameChange(id: SObjectId, newLastName: String?)
    fun onTitleChange(id: SObjectId, newTitle: String?)
    fun onDepartmentChange(id: SObjectId, newDepartment: String?)
}
