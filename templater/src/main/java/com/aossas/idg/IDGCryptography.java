package com.aossas.idg;

import java.security.SecureRandom;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import javax.xml.bind.DatatypeConverter;

public class IDGCryptography {

	public static void main(String[] args) throws Exception {
		String hexKey = "0123456789ABCDEFFEDCBA98765432100123456789ABCDEFFEDCBA9876543210";
		String plainText = "abcde12345";

		byte[] encrypted = encrypt(plainText, hexKey);
		System.out.println("encrypted: " + Base64.getEncoder().encodeToString(encrypted));

		String decrypted = decrypt(encrypted, hexKey);
		System.out.println("decrypted: " + decrypted);
	}

	public static byte[] encrypt(String plainText, String hexKey) throws Exception {
		byte[] clean = plainText.getBytes();

		// Generating IV.
		int ivSize = 16;
		byte[] iv = new byte[ivSize];
		SecureRandom random = new SecureRandom();
		random.nextBytes(iv);
		IvParameterSpec ivParameterSpec = new IvParameterSpec(iv);

		// Key spec.
		SecretKeySpec secretKeySpec = new SecretKeySpec(DatatypeConverter.parseHexBinary(hexKey), "AES");

		// Encrypt.
		Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
		cipher.init(Cipher.ENCRYPT_MODE, secretKeySpec, ivParameterSpec);
		byte[] encrypted = cipher.doFinal(clean);

		// Combine IV and encrypted part.
		byte[] encryptedIVAndText = new byte[ivSize + encrypted.length];
		System.arraycopy(iv, 0, encryptedIVAndText, 0, ivSize);
		System.arraycopy(encrypted, 0, encryptedIVAndText, ivSize, encrypted.length);

		return encryptedIVAndText;
	}

	public static String decrypt(byte[] encryptedIvTextBytes, String hexKey) throws Exception {
		// Extract IV.
		int ivSize = 16;
		byte[] iv = new byte[ivSize];
		System.arraycopy(encryptedIvTextBytes, 0, iv, 0, iv.length);
		IvParameterSpec ivParameterSpec = new IvParameterSpec(iv);

		// Extract encrypted part.
		int encryptedSize = encryptedIvTextBytes.length - ivSize;
		byte[] encryptedBytes = new byte[encryptedSize];
		System.arraycopy(encryptedIvTextBytes, ivSize, encryptedBytes, 0, encryptedSize);

		// Key spec.
		SecretKeySpec secretKeySpec = new SecretKeySpec(DatatypeConverter.parseHexBinary(hexKey), "AES");

		// Decrypt.
		Cipher cipherDecrypt = Cipher.getInstance("AES/CBC/PKCS5Padding");
		cipherDecrypt.init(Cipher.DECRYPT_MODE, secretKeySpec, ivParameterSpec);
		byte[] decrypted = cipherDecrypt.doFinal(encryptedBytes);

		return new String(decrypted);
	}
	
}