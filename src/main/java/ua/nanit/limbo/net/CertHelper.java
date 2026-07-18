package ua.nanit.limbo.net;

import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openssl.jcajce.JcaPEMWriter;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;

import java.io.*;
import java.math.BigInteger;
import java.security.*;
import java.security.cert.X509Certificate;
import java.util.Date;
import java.util.concurrent.TimeUnit;

public class CertHelper {

    private static final String KEY_FILE = "key.pem";
    private static final String CERT_FILE = "cert.pem";

    static {
        Security.addProvider(new BouncyCastleProvider());
    }

    public static void generate(String commonName, int days, int keySize, File path) throws Exception {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA", "BC");
        kpg.initialize(keySize, new SecureRandom());
        KeyPair keyPair = kpg.generateKeyPair();

        long now = System.currentTimeMillis();
        X500Name subject = new X500Name("CN=" + commonName);

        X509v3CertificateBuilder builder = new X509v3CertificateBuilder(
                subject, BigInteger.valueOf(now), new Date(now),
                new Date(now + TimeUnit.DAYS.toMillis(days)), subject,
                SubjectPublicKeyInfo.getInstance(keyPair.getPublic().getEncoded()));

        ContentSigner signer = new JcaContentSignerBuilder("SHA256WithRSA")
                .setProvider("BC").build(keyPair.getPrivate());

        X509Certificate cert = new JcaX509CertificateConverter()
                .setProvider("BC").getCertificate(builder.build(signer));

        cert.verify(keyPair.getPublic());

        saveAsPem(keyPair.getPrivate(), new File(path, KEY_FILE));
        saveAsPem(cert, new File(path, CERT_FILE));
    }

    private static void saveAsPem(Object obj, File file) throws IOException {
        try (Writer writer = new FileWriter(file);
             JcaPEMWriter pemWriter = new JcaPEMWriter(writer)) {
            pemWriter.writeObject(obj);
        }
    }
}
