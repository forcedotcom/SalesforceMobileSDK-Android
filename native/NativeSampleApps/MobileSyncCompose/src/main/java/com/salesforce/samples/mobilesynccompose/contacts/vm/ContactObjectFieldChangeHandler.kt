package com.salesforce.samples.mobilesynccompose.contacts.vm

interface ContactObjectFieldChangeHandler {
    fun onFirstNameChange(newFirstName: String)
    fun onLastNameChange(newLastName: String)
    fun onTitleChange(newTitle: String)
    fun onDepartmentChange(newDepartment: String)
}
