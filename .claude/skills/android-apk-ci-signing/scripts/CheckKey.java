// Loads a private key the way Android release signing does (KeyStore.getKey). Unlike keytool, this
// also rejects a wrong key password for PKCS12 keystores.
// Usage: printf '%s\n%s\n' "$STORE_PASSWORD" "$KEY_PASSWORD" | java CheckKey.java <keystore> <alias>
// Exit codes: 0 ok, 2 wrong store password (or not a keystore), 3 no key under this alias, 4 wrong key password.
import java.io.*;
import java.security.*;

public class CheckKey {
    public static void main(String[] args) throws Exception {
        BufferedReader in = new BufferedReader(new InputStreamReader(System.in));
        char[] storePassword = in.readLine().toCharArray();
        char[] keyPassword = in.readLine().toCharArray();
        KeyStore keyStore;
        try {
            keyStore = KeyStore.getInstance(new File(args[0]), storePassword);
        } catch (IOException | GeneralSecurityException e) {
            System.exit(2); return;
        }
        if (!keyStore.isKeyEntry(args[1])) { System.exit(3); return; }
        try {
            if (keyStore.getKey(args[1], keyPassword) == null) System.exit(4);
        } catch (UnrecoverableKeyException e) {
            System.exit(4);
        }
    }
}
