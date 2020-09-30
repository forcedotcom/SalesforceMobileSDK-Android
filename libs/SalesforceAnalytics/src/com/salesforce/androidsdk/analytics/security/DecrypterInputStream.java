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

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.List;

import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;

/** Input stream that decrypts content written with a EncrypterOutputStream */
public class DecrypterInputStream extends InputStream implements WatchableStream {

    private InputStream cipherInputStream;
    private List<Watcher> watchers;

    public DecrypterInputStream(FileInputStream inputStream, String encryptionKey)
            throws GeneralSecurityException, IOException {
        // First byte should be iv length
        int ivLength = inputStream.read();

        // IV length is always 12 for AES-GCM-256.
        if (ivLength != 12) {
            throw new IOException("Can't decrypt file: incorrect iv length found in file: " + ivLength);
        }
        // Next bytes should be iv
        byte[] iv = new byte[ivLength];
        inputStream.read(iv);
        Cipher cipher = Encryptor.getDecryptingCipher(encryptionKey, iv);
        cipherInputStream = new CipherInputStream(inputStream, cipher);
        watchers = new ArrayList<>();
    }

    public DecrypterInputStream() {
        throw new IllegalArgumentException("Constructor not supported");
    }

    @Override
    public int read(byte[] b) throws IOException {
        return cipherInputStream.read(b);
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        return cipherInputStream.read(b, off, len);
    }

    @Override
    public long skip(long n) throws IOException {
        return cipherInputStream.skip(n);
    }

    @Override
    public int available() throws IOException {
        return cipherInputStream.available();
    }

    @Override
    public void close() throws IOException {
        if (!watchers.isEmpty()) {
            for (Watcher watcher : watchers) {
                watcher.onClose();
            }
        }
        cipherInputStream.close();
    }

    @Override
    public synchronized void mark(int readlimit) {
        cipherInputStream.mark(readlimit);
    }

    @Override
    public synchronized void reset() throws IOException {
        cipherInputStream.reset();
    }

    @Override
    public boolean markSupported() {
        return cipherInputStream.markSupported();
    }

    @Override
    public int read() throws IOException {
        return cipherInputStream.read();
    }

    public void addWatcher(Watcher watcher) {
        this.watchers.add(watcher);
    }
}
