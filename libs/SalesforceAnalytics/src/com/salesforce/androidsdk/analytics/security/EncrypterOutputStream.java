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

package com.salesforce.androidsdk.analytics.security;

import com.salesforce.androidsdk.analytics.util.WatchableStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.List;
import javax.crypto.Cipher;
import javax.crypto.CipherOutputStream;

/** Output stream that encrypts content - can be read back with a DecrypterInputStream */
public class EncrypterOutputStream extends OutputStream implements WatchableStream {

    private OutputStream cipherOutputStream;
    private List<Watcher> watchers;

    public EncrypterOutputStream(FileOutputStream outputStream, String encryptionKey)
            throws GeneralSecurityException, IOException {
        final Cipher cipher = Encryptor.getEncryptingCipher(encryptionKey);
        final byte[] iv = cipher.getIV();
        // First byte should be iv length
        outputStream.write(iv.length);
        // Next bytes should be iv
        outputStream.write(cipher.getIV());
        cipherOutputStream = new CipherOutputStream(outputStream, cipher);
        watchers = new ArrayList<>();
    }

    @Override
    public void write(byte[] b) throws IOException {
        cipherOutputStream.write(b);
    }

    @Override
    public void write(byte[] b, int off, int len) throws IOException {
        cipherOutputStream.write(b, off, len);
    }

    @Override
    public void flush() throws IOException {
        cipherOutputStream.flush();
    }

    @Override
    public void close() throws IOException {
        if (!watchers.isEmpty()) {
            for (Watcher watcher : watchers) {
                watcher.onClose();
            }
        }
        cipherOutputStream.close();
    }

    @Override
    public void write(int i) throws IOException {
        cipherOutputStream.write(i);
    }

    public void addWatcher(Watcher watcher) {
        this.watchers.add(watcher);
    }
}
