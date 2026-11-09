/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package net.quimmilho.pokednd.api.auth;

import io.quarkus.mailer.Mail;
import io.quarkus.mailer.Mailer;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import org.jboss.logging.Logger;

/**
 * Sends the account mails (chapter 16.4) in the person's language, English or Portuguese. The texts are in
 * {@code resources/mail/mail_en.properties} and {@code mail_pt.properties}; {@code %NAME%}, {@code %LINK%}, {@code %HOURS%} and
 * {@code %MINUTES%} are replaced.
 */
@ApplicationScoped
public class MailService {

    private static final Logger LOG = Logger.getLogger(MailService.class);

    @Inject
    Mailer mailer;

    private final Map<String, Properties> texts = new ConcurrentHashMap<>();

    /**
     * Sends a mail made from the texts {@code key}.subject and {@code key}.body. A mail that cannot be sent is logged and
     * swallowed: the account flows must not fail because the mail server is down (the person can ask again). The address is
     * not written to the log (chapter 16.7).
     *
     * @return true if the mail server took the mail
     */
    public boolean send(String to, String locale, String key, Map<String, String> values) {
        Properties p = texts("pt".equals(locale) ? "pt" : "en");
        String subject = p.getProperty(key + ".subject");
        String body = p.getProperty(key + ".body");
        for (Map.Entry<String, String> e : values.entrySet()) {
            body = body.replace("%" + e.getKey() + "%", e.getValue());
        }
        try {
            mailer.send(Mail.withText(to, subject, body));
            return true;
        } catch (RuntimeException e) {
            LOG.warnf("Could not send the %s mail: %s", key, e.getMessage());
            return false;
        }
    }

    private Properties texts(String language) {
        return texts.computeIfAbsent(language, l -> {
            Properties p = new Properties();
            try (InputStream in = MailService.class.getResourceAsStream("/mail/mail_" + l + ".properties")) {
                p.load(new InputStreamReader(in, StandardCharsets.UTF_8));
            } catch (IOException | NullPointerException e) {
                throw new IllegalStateException("The mail texts for " + l + " are missing", e);
            }
            return p;
        });
    }
}
