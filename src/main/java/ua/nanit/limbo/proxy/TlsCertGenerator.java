package ua.nanit.limbo.proxy;

import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.GeneralName;
import org.bouncycastle.asn1.x509.GeneralNames;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openssl.jcajce.JcaPEMWriter;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import ua.nanit.limbo.server.Log;

import java.io.File;
import java.io.FileWriter;
import java.math.BigInteger;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.SecureRandom;
import java.security.Security;
import java.security.cert.X509Certificate;
import java.util.Calendar;
import java.util.Date;

public class TlsCertGenerator {

    static {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }

    public static void generate(String commonName, int days, int keySize, File certPath) throws Exception {
        Log.info("Generating TLS certificate for %s...", commonName);

        KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA", "BC");
        keyPairGenerator.initialize(keySize, new SecureRandom());
        KeyPair keyPair = keyPairGenerator.generateKeyPair();

        Date now = new Date();
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(now);
        calendar.add(Calendar.DAY_OF_YEAR, days);
        Date endDate = calendar.getTime();

        BigInteger serial = new BigInteger(160, new SecureRandom());
        X500Name issuer = new X500Name("CN=" + commonName);
        X500Name subject = issuer;

        X509v3CertificateBuilder certBuilder = new JcaX509v3CertificateBuilder(
                issuer,
                serial,
                now,
                endDate,
                subject,
                keyPair.getPublic()
        );

        GeneralName[] sanNames = new GeneralName[]{
                new GeneralName(GeneralName.dNSName, commonName),
                new GeneralName(GeneralName.dNSName, "*." + commonName)
        };
        certBuilder.addExtension(Extension.subjectAlternativeName, false, new GeneralNames(sanNames));

        ContentSigner signer = new JcaContentSignerBuilder("SHA256WithRSA")
                .setProvider("BC")
                .build(keyPair.getPrivate());

        X509CertificateHolder certHolder = certBuilder.build(signer);
        X509Certificate certificate = new JcaX509CertificateConverter()
                .setProvider("BC")
                .getCertificate(certHolder);

        if (!certPath.exists()) {
            certPath.mkdirs();
        }

        File keyFile = new File(certPath, "key.pem");
        try (JcaPEMWriter pemWriter = new JcaPEMWriter(new FileWriter(keyFile))) {
            pemWriter.writeObject(keyPair.getPrivate());
        }

        File certFile = new File(certPath, "cert.pem");
        try (JcaPEMWriter pemWriter = new JcaPEMWriter(new FileWriter(certFile))) {
            pemWriter.writeObject(certificate);
        }

        Log.info("TLS certificate generated successfully: %s, %s", keyFile.getPath(), certFile.getPath());
    }
}
