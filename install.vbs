Option Explicit
On Error Resume Next

' ***                                                                      ***
' *                                                                          *
' * ATTENTION: Windows Users                                                 *
' *                                                                          *
' * Run this script to initialize the submodules of your repository, and fix *
' * symlinks, which otherwise won't work on Windows.  You *must* run this    *
' * script if you plan to work with the Salesforce Mobile SDK for Android on *
' * Windows.                                                                 *
' *    From the command line: cscript install.vbs                            *
' ***                                                                      ***

Dim strWorkingDirectory, strSymlinkFilesListPath, objShell, intReturnVal, _
    strErrorDescription, objFso

' Set the working folder to the script location (i.e. the repo root)
strWorkingDirectory = GetDirectoryName(WScript.ScriptFullName)
Set objShell = WScript.CreateObject("WScript.Shell")
objShell.CurrentDirectory = strWorkingDirectory
strSymlinkFilesListPath = strWorkingDirectory & "tools\symlink_files.txt"

' If the symlink files list does not exist, we can't continue.
Set objFso = WScript.CreateObject("Scripting.FileSystemObject")
If Not objFso.FileExists(strSymlinkFilesListPath) Then
    WScript.Echo "The file '" & strSymlinkFilesListPath & "' does not exist.  Cannot continue."
    Set objFso = Nothing
    WScript.Quit 9
End If
Set objFso = Nothing

' If git is not in the path, this script cannot run.
Set objShell = WScript.CreateObject("WScript.Shell")
intReturnVal = objShell.Run("git status", 1, True)
If Err.number <> 0 Then
    strErrorDescription = Err.Description
    Err.Clear
    WScript.Echo "There was an error getting the status of the git repository: '" & strErrorDescription & "' Make sure the 'git' executable is on your PATH variable."
    WScript.Quit 1
End If

If intReturnVal <> 0 Then
    If intReturnVal = 128 Then
        WScript.Echo "Git repository not initialized!  You must clone this repository via the 'git clone' command to run this script."
        WScript.Quit 7
    Else
        WScript.Echo "Unknown error getting the git repository status (" & intReturnVal & ").  Make sure git is installed and part of your PATH, and that you've cloned the git repository using the 'git clone' command."
        WScript.Quit 8
    End If
End If

' Initialze and update the submodules.
intReturnVal = objShell.Run("git submodule init", 1, True)
If intReturnVal <> 0 Then
    WScript.Echo "Error initializing the submodules!"
    WScript.Quit 2
End If
intReturnVal = objShell.Run("git submodule sync", 1, True)
If intReturnVal <> 0 Then
    WScript.Echo "Error syncing the submodules!"
    WScript.Quit 6
End If
intReturnVal = objShell.Run("git submodule update", 1, True)
If intReturnVal <> 0 Then
    WScript.Echo "Error updating the submodules!"
    WScript.Quit 3
End If

' Copy the hard files to where their symlinks would be.
Call CopySymlinkFiles()

' Getting npm dependencies for react-native
WScript.Echo "Getting npm dependencies"
objShell.CurrentDirectory = strWorkingDirectory & "libs\SalesforceReact"
intReturnVal = objShell.Run("npm install --silent", 1, True)
objShell.CurrentDirectory = strWorkingDirectory
If intReturnVal <> 0 Then
    WScript.Echo "Error getting npm dependencies!"
    WScript.Quit 2
End If


WScript.Echo vbCrLf & "Successfully initialized submodules, fixed symlinks and downloaded npm dependencies."
WScript.Quit 0


' -------------------------------------------------------------------
' - Reads a list of generated source and destination files associated
' - with symlinks, and copies the source into the destination.
' -------------------------------------------------------------------
Sub CopySymlinkFiles
    Dim objAdoStream, strFilesLine, objFso, objRegExp, objMatches, objMatch, strSrcFile, strDestFile, bIsFolder
    
    ' Open the list of source and destination files for reading.
    Set objAdoStream = WScript.CreateObject("ADODB.Stream")
    objAdoStream.Open
    objAdoStream.Type = 2  ' Text stream
    objAdoStream.Charset = "utf-8"
    'objAdoStream.Mode = 1  ' Read-only
    WScript.Echo
    objAdoStream.LoadFromFile strSymlinkFilesListPath
    If Err.number <> 0 Then
        strErrorDescription = Err.Description
        Err.Clear
        WScript.Echo "Error opening '" & strSymlinkFilesListPath & "' for reading: " & strErrorDescription
        Set objAdoStream = Nothing
        WScript.Quit 4
    End If
    
    ' Parse the entries in the file, make the file copies.
    Set objFso = WScript.CreateObject("Scripting.FileSystemObject")
    Set objRegExp = New RegExp
    objRegExp.Pattern = """([^""]+)"" +""([^""]+)"""
    objRegExp.Global = True
    While Not objAdoStream.EOS
        strFilesLine = objAdoStream.ReadText(-2)  ' Line by line.
        strFilesLine = Trim(strFilesLine)
        If strFilesLine <> "" Then
            Set objMatches = objRegExp.Execute(strFilesLine)
	    Set objMatch = objMatches.Item(0)
	    strSrcFile = objMatch.SubMatches.Item(0)
	    strDestFile = objMatch.SubMatches.Item(1)

            ' Make sure source file exists.
            If Not objFso.FileExists(strSrcFile) And Not objFso.FolderExists(strSrcFile) Then
                WScript.Echo "Source file '" & strSrcFile & "' does not exist!  Cannot continue."
                Set objFso = Nothing
                objAdoStream.Close
                Set objAdoStream = Nothing
                WScript.Quit 10
            End If

	    WScript.Echo "Copying '" & strSrcFile & "' to '" & strDestFile & "'"
	    If objFso.FileExists(strDestFile) Then
	        objFso.DeleteFile strDestFile, True
	    End If
	    
	    bIsFolder = objFso.FolderExists(strSrcFile)
	    If Not bIsFolder Then
	        objFso.CopyFile strSrcFile, strDestFile, True  ' Overwrite
	    Else
	        objFso.CopyFolder strSrcFile, strDestFile, True  ' Overwrite
	    End If
	    If Err.number <> 0 Then
                strErrorDescription = Err.Description
                Err.Clear
	        WScript.Echo "Error copying '" & strSrcFile & "' to '" & strDestFile & "': " & strErrorDescription
	        Set objFso = Nothing
	        objAdoStream.Close
	        Set objAdoStream = Nothing
	        WScript.Quit 5
            End If
        End If
    Wend
    
    ' Cleanup.
    Set objFso = Nothing
    objAdoStream.Close
    Set objAdoStream = Nothing
End Sub


' -------------------------------------------------------------------
' - Gets the directory name, from a file path.
' -------------------------------------------------------------------
Function GetDirectoryName(ByVal strFilePath)
    Dim strFinalSlash
    strFinalSlash = InStrRev(strFilePath, "\")
    If strFinalSlash = 0 Then
        GetDirectoryName = strFilePath
    Else
        GetDirectoryName = Left(strFilePath, strFinalSlash)
    End If
End Function
