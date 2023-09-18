/*
 * Copyright (C) 2022-2023 Velocity Contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

// implementacja IdentifiedKey i EncryptionUtils z wersji 3.0.0 Velocity

package pl.fratik.mcs.encryption;

import com.google.common.base.Objects;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.checkerframework.checker.nullness.qual.Nullable;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.security.PublicKey;
import java.time.Instant;
import java.util.Arrays;
import java.util.UUID;

/**
 * Represents the contents of a {@link IdentifiedKey}.
 */
public class IdentifiedKey {
    private final PublicKey publicKey;
    private final byte[] signature;
    private final Instant expiryTemporal;
    private @MonotonicNonNull Boolean isSignatureValid;
    private @MonotonicNonNull UUID holder;

    public IdentifiedKey(byte[] keyBits, long expiry, byte[] signature) {
        this(EncryptionUtils.parseRsaPublicKey(keyBits), Instant.ofEpochMilli(expiry), signature);
    }

    /**
     * Creates an Identified key from data.
     */
    public IdentifiedKey(PublicKey publicKey, Instant expiryTemporal, byte[] signature) {
        this.publicKey = publicKey;
        this.expiryTemporal = expiryTemporal;
        this.signature = signature;
    }

    public PublicKey getSignedPublicKey() {
        return publicKey;
    }

    public PublicKey getSigner() {
        return EncryptionUtils.getYggdrasilSessionKey();
    }

    public Instant getExpiryTemporal() {
        return expiryTemporal;
    }

    public byte[] getSignature() {
        return signature.clone();
    }

    public @Nullable UUID getSignatureHolder() {
        return holder;
    }

    /**
     * Sets the uuid for this key. Returns false if incorrect.
     */
    public boolean internalAddHolder(UUID holder) {
        if (holder == null) {
            return false;
        }
        if (this.holder == null) {
            Boolean result = validateData(holder);
            if (result == null || !result) {
                return false;
            }
            isSignatureValid = true;
            this.holder = holder;
            return true;
        }
        return this.holder.equals(holder) && isSignatureValid();
    }

    public boolean isSignatureValid() {
        if (isSignatureValid == null) {
            isSignatureValid = validateData(holder);
        }
        return isSignatureValid != null && isSignatureValid;
    }

    private Boolean validateData(@Nullable UUID verify) {
        if (verify == null) {
            return null;
        }
        byte[] keyBytes = publicKey.getEncoded();
        byte[] toVerify = new byte[keyBytes.length + 24]; // length long * 3
        ByteBuffer fixedDataSet = ByteBuffer.wrap(toVerify).order(ByteOrder.BIG_ENDIAN);
        fixedDataSet.putLong(verify.getMostSignificantBits());
        fixedDataSet.putLong(verify.getLeastSignificantBits());
        fixedDataSet.putLong(expiryTemporal.toEpochMilli());
        fixedDataSet.put(keyBytes);
        return EncryptionUtils.verifySignature(EncryptionUtils.SHA1_WITH_RSA,
                EncryptionUtils.getYggdrasilSessionKey(), signature, toVerify);
    }

    public boolean verifyDataSignature(byte[] signature, byte[]... toVerify) {
        try {
            return EncryptionUtils.verifySignature(EncryptionUtils.SHA256_WITH_RSA, publicKey, signature,
                    toVerify);
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    public String toString() {
        return "IdentifiedKeyImpl{"
                + ", publicKey=" + publicKey
                + ", signature=" + Arrays.toString(signature)
                + ", expiryTemporal=" + expiryTemporal
                + ", isSignatureValid=" + isSignatureValid
                + ", holder=" + holder
                + '}';
    }

    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof IdentifiedKey)) {
            return false;
        }

        IdentifiedKey that = (IdentifiedKey) o;

        return Objects.equal(this.getSignedPublicKey(), that.getSignedPublicKey())
                && Objects.equal(this.getExpiryTemporal(), that.getExpiryTemporal())
                && Arrays.equals(this.getSignature(), that.getSignature())
                && Objects.equal(this.getSigner(), that.getSigner());
    }
}
