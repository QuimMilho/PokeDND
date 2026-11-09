/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package net.quimmilho.pokednd.api;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A tiny SMTP server for the tests: it accepts every mail and keeps it, so a test can read the link that the API mailed. It speaks
 * just enough SMTP (EHLO, MAIL, RCPT, DATA, RSET, NOOP, QUIT) and understands quoted-printable and base64 bodies.
 */
public final class FakeSmtpServer implements AutoCloseable {

    /** A mail the server received, with the subject and the text already decoded. */
    public record Mail(String from, List<String> to, String subject, String body) {

        /** The first web link in the text (the verification or reset link). */
        public String link() {
            Matcher m = Pattern.compile("https?://\\S+").matcher(body);
            if (!m.find()) {
                throw new AssertionError("no link in the mail: " + body);
            }
            return m.group();
        }

        /** The value of the {@code token} parameter of the link. */
        public String token() {
            String link = link();
            int i = link.indexOf("token=");
            if (i < 0) {
                throw new AssertionError("no token in the link: " + link);
            }
            return java.net.URLDecoder.decode(link.substring(i + "token=".length()), StandardCharsets.UTF_8);
        }
    }

    private final ServerSocket server;
    private final List<Mail> mails = new CopyOnWriteArrayList<>();

    public FakeSmtpServer() throws IOException {
        server = new ServerSocket(0, 50, InetAddress.getLoopbackAddress());
        Thread acceptor = new Thread(this::acceptLoop, "fake-smtp");
        acceptor.setDaemon(true);
        acceptor.start();
    }

    public int port() {
        return server.getLocalPort();
    }

    public void clear() {
        mails.clear();
    }

    /** The mails sent to this address, oldest first. */
    public List<Mail> to(String address) {
        return mails.stream().filter(m -> m.to().stream().anyMatch(t -> t.equalsIgnoreCase(address))).toList();
    }

    /** Waits (up to 5 seconds) until {@code count} mails to this address have arrived, and returns them. */
    public List<Mail> await(String address, int count) {
        long end = System.currentTimeMillis() + 5000;
        while (System.currentTimeMillis() < end) {
            List<Mail> found = to(address);
            if (found.size() >= count) {
                return found;
            }
            try {
                Thread.sleep(20);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        throw new AssertionError("expected " + count + " mail(s) to " + address + " but got " + to(address).size());
    }

    /** The newest mail to this address; fails if none arrives. */
    public Mail awaitNewest(String address) {
        List<Mail> all = await(address, 1);
        return all.get(all.size() - 1);
    }

    @Override
    public void close() throws IOException {
        server.close();
    }

    // ------------------------------------------------------------------ the protocol

    private void acceptLoop() {
        while (!server.isClosed()) {
            try {
                Socket s = server.accept();
                Thread t = new Thread(() -> serve(s), "fake-smtp-connection");
                t.setDaemon(true);
                t.start();
            } catch (IOException e) {
                return;
            }
        }
    }

    private void serve(Socket socket) {
        try (socket; BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.ISO_8859_1));
             OutputStream out = socket.getOutputStream()) {
            reply(out, "220 fake-smtp ready");
            String from = "";
            List<String> to = new ArrayList<>();
            String line;
            while ((line = in.readLine()) != null) {
                String cmd = line.toUpperCase(Locale.ROOT);
                if (cmd.startsWith("EHLO") || cmd.startsWith("HELO")) {
                    reply(out, "250 fake-smtp");
                } else if (cmd.startsWith("MAIL FROM")) {
                    from = address(line);
                    to = new ArrayList<>();
                    reply(out, "250 OK");
                } else if (cmd.startsWith("RCPT TO")) {
                    to.add(address(line));
                    reply(out, "250 OK");
                } else if (cmd.startsWith("DATA")) {
                    reply(out, "354 End data with <CR><LF>.<CR><LF>");
                    StringBuilder data = new StringBuilder();
                    while ((line = in.readLine()) != null && !line.equals(".")) {
                        data.append(line.startsWith("..") ? line.substring(1) : line).append("\r\n");
                    }
                    mails.add(parse(from, to, data.toString()));
                    reply(out, "250 OK queued");
                } else if (cmd.startsWith("RSET") || cmd.startsWith("NOOP")) {
                    reply(out, "250 OK");
                } else if (cmd.startsWith("QUIT")) {
                    reply(out, "221 bye");
                    return;
                } else {
                    reply(out, "502 not implemented");
                }
            }
        } catch (IOException ignored) {
            // the client went away
        }
    }

    private static void reply(OutputStream out, String text) throws IOException {
        out.write((text + "\r\n").getBytes(StandardCharsets.ISO_8859_1));
        out.flush();
    }

    private static String address(String line) {
        Matcher m = Pattern.compile("<([^>]*)>").matcher(line);
        return m.find() ? m.group(1) : line.substring(line.indexOf(':') + 1).strip();
    }

    // ------------------------------------------------------------------ reading a mail

    static Mail parse(String from, List<String> to, String raw) {
        int split = raw.indexOf("\r\n\r\n");
        String headerText = split < 0 ? raw : raw.substring(0, split);
        String body = split < 0 ? "" : raw.substring(split + 4);
        // headers can continue on the next line when that starts with a space
        String headers = headerText.replaceAll("\r\n[ \t]+", " ");
        String subject = decodeWords(header(headers, "Subject"));
        String encoding = header(headers, "Content-Transfer-Encoding").toLowerCase(Locale.ROOT);
        String charset = "UTF-8";
        Matcher cs = Pattern.compile("charset=\"?([A-Za-z0-9_-]+)", Pattern.CASE_INSENSITIVE).matcher(header(headers, "Content-Type"));
        if (cs.find()) {
            charset = cs.group(1);
        }
        if (header(headers, "Content-Type").toLowerCase(Locale.ROOT).startsWith("multipart/")) {
            throw new AssertionError("the fake SMTP server reads single-part mails only: " + headers);
        }
        return new Mail(from, List.copyOf(to), subject, decodeBody(body, encoding, charset));
    }

    private static String header(String headers, String name) {
        Matcher m = Pattern.compile("(?im)^" + Pattern.quote(name) + ":[ \t]*(.*)$").matcher(headers);
        return m.find() ? m.group(1).strip() : "";
    }

    private static String decodeBody(String body, String encoding, String charset) {
        byte[] bytes;
        if (encoding.equals("base64")) {
            bytes = Base64.getMimeDecoder().decode(body);
        } else if (encoding.equals("quoted-printable")) {
            bytes = quotedPrintable(body);
        } else {
            bytes = body.getBytes(StandardCharsets.ISO_8859_1);
        }
        return new String(bytes, java.nio.charset.Charset.forName(charset));
    }

    private static byte[] quotedPrintable(String text) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        String s = text.replace("=\r\n", "");
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '=' && i + 2 < s.length() + 0 && isHex(s.charAt(i + 1)) && isHex(s.charAt(i + 2))) {
                out.write(Integer.parseInt(s.substring(i + 1, i + 3), 16));
                i += 2;
            } else {
                out.write(c);
            }
        }
        return out.toByteArray();
    }

    private static boolean isHex(char c) {
        return Character.digit(c, 16) >= 0;
    }

    /** Decodes {@code =?UTF-8?B?...?=} and {@code =?UTF-8?Q?...?=} words of a header. */
    private static String decodeWords(String value) {
        Matcher m = Pattern.compile("=\\?([^?]+)\\?([BbQq])\\?([^?]*)\\?=").matcher(value.replaceAll("\\?=\\s+=\\?", "?==?"));
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            java.nio.charset.Charset cs = java.nio.charset.Charset.forName(m.group(1));
            byte[] bytes = m.group(2).equalsIgnoreCase("B") ? Base64.getDecoder().decode(m.group(3))
                    : quotedPrintable(m.group(3).replace('_', ' '));
            m.appendReplacement(sb, Matcher.quoteReplacement(new String(bytes, cs)));
        }
        m.appendTail(sb);
        return sb.toString();
    }
}
