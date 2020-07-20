/*
 * Copyright (c) 2020-present, salesforce.com, inc.
 * All rights reserved.
 * Redistribution and use of this software in source and binary forms, with or
 * without modification, are permitted provided that the following conditions
 * are met:
 * - Redistributions of source code must retain the above copyright notice, this
 * list of conditions and the following disclaimer.
 * - Redistributions in binary form must reproduce the above copyright notice,
 * this list of conditions and the following disclaimer in the documentation
 * and/or other materials provided with the distribution.
 * - Neither the name of salesforce.com, inc. nor the names of its contributors
 * may be used to endorse or promote products derived from this software without
 * specific prior written permission of salesforce.com, inc.
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */
package com.salesforce.androidsdk.util;

import android.content.Context;
import android.text.TextUtils;
import java.io.File;
import java.io.FilenameFilter;
import java.util.ArrayList;
import java.util.List;

/**
 * Utility class for listing / removing files "managed" by Mobile SDK.
 */
public class ManagedFilesHelper {

  /**
   * Get files with the given suffix and extension that live under dirName
   * excluding the ones with excludingSuffix.
   *
   * @return Array of File
   */
  public static File[] getFiles(Context ctx, String dirName, final String suffix, final String extension, final String excludingSuffix) {
    File dir = new File(ctx.getApplicationInfo().dataDir, dirName);
    File[] files = dir.listFiles(new FilenameFilter() {
      @Override
      public boolean accept(File dir, String name) {
        return name.contains(suffix) && name.endsWith(extension) && (TextUtils.isEmpty(excludingSuffix) || !name.contains(excludingSuffix));
      }
    });
    return files == null ? new File[0] : files;
  }

  /**
   * Get "short name" (prefix) of managed files with the given suffix and extension that live
   * under dirName excluding the ones with excludingSuffix.
   * Instead of the file names, we return the "short name" or prefix i.e. the part of the name
   * before the suffix / extension.
   * @return List of names
   */
  public static List<String> getPrefixList(Context ctx, String dirName, String suffix, String extension, String excludingSuffix) {
    ArrayList<String> prefixList = new ArrayList<>();
    File[] files = getFiles(ctx, dirName, suffix, extension, excludingSuffix);
    for (File file : files) {
      String fileName = file.getName();
      int lengthToKeep =
          !TextUtils.isEmpty(suffix) ? fileName.indexOf(suffix) : fileName.indexOf(extension);
      prefixList.add(fileName.substring(0, lengthToKeep));
    }
    return prefixList;
  }

  /**
   * Delete fileOrDir or directory (recursively)
   *
   * @return boolean if successful
   */
  public static boolean deleteFile(File fileOrDir) {
    if (fileOrDir == null  || !fileOrDir.exists()) {
      return true; // we didn't fail - there was nothing to do
    }

    if (fileOrDir.isFile()) {
      return fileOrDir.delete();
    } else {
      return deleteFiles(fileOrDir.listFiles()) && fileOrDir.delete();
    }
  }

  /**
   * Delete fileOrDir or directory (recursively)
   *
   * @return boolean if successful
   */
  public static boolean deleteFiles(File[] files) {
    if (files == null) {
      return true; // we didn't fail - there was nothing to do
    }

    boolean success = true;
    for (File file : files) {
      success &= deleteFile(file);
    }
    return success;
  }

}
