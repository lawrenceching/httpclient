package org.example;

import com.google.common.util.concurrent.RateLimiter;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

public class Main {

    public static void main(String[] args) throws InterruptedException {
        new Main().start(args);
    }


    public void start(String[] args) throws InterruptedException {

        int clients = 1;
        int qps = 10;
        String proxy = null;
        String url = null;
        boolean http2PriorKnowledge = true;
        int number = 0;
        for (String arg : args) {

            String v;

            if ((v = readArgValue(arg, "--client=")) != null) {
                clients = Integer.parseInt(v);
                continue;
            }

            if ((v = readArgValue(arg, "--qps=")) != null) {
                qps = Integer.parseInt(v);
                continue;
            }

            if ((v = readArgValue(arg, "--number=")) != null) {
                number = Integer.parseInt(v);
                continue;
            }

            if ((v = readArgValue(arg, "--proxy=")) != null) {
                proxy = v;
                continue;
            }

            if ((v = readArgValue(arg, "--http2-prior-knowledge=")) != null) {
                http2PriorKnowledge = Boolean.parseBoolean(v);
                continue;
            }

            if ((v = readArgValue(arg, "--url=")) != null) {
                url = v;
                continue;
            }

            throw new IllegalArgumentException(String.format("Argument %s is not supported", arg));
        }

        assertNotEmpty(url, "Not url provided, please set url by --url=<...>");

        info(String.format("""
                clients=%s
                qps=%s,
                proxy=%s,
                http2PriorKnowledge=%s
                url=%s,
                number=%s""", clients, qps, proxy, http2PriorKnowledge, url, number));


        RateLimiter rateLimiter = RateLimiter.create(qps);
        AtomicLong successCount = new AtomicLong();
        AtomicLong failureCount = new AtomicLong();

        final String targetUrl = url;

        CountDownLatch latch = null;
        if(number > 0) {
            latch = new CountDownLatch(number);
        }
        final CountDownLatch _latch = latch;
        final int _number = number;

        ExecutorService workers = Executors.newFixedThreadPool(clients);
        for (int i = 0; i < clients; i++) {

            OkHttpClient.Builder builder = new OkHttpClient.Builder();
            if (http2PriorKnowledge) builder.protocols(Collections.singletonList(Protocol.H2_PRIOR_KNOWLEDGE));
            if (proxy != null) {
                String[] words = proxy.split(":");
                String host = words[0];
                int port = Integer.parseInt(words[1]);
                builder.proxy(new Proxy(Proxy.Type.HTTP, new InetSocketAddress(host, port)));
            }

            OkHttpClient client = builder.build();

            workers.submit(() -> {
                while (!Thread.currentThread().isInterrupted() && (_latch == null || _latch.getCount() > 0)
                ) {
                    rateLimiter.acquire();
                    Request req = new Request.Builder()
                            .url(targetUrl)
                            .build();

                    try (Response resp = client.newCall(req).execute()) {
                        if (resp.isSuccessful()) {
                            successCount.incrementAndGet();
                        } else {
                            failureCount.incrementAndGet();
                        }
                    } catch (IOException e) {
                        failureCount.incrementAndGet();
                        e.printStackTrace();
                    } finally {
                        if(_latch != null) _latch.countDown();
                    }
                }
            });
        }


        ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

        scheduler.scheduleAtFixedRate(() -> {
            info(String.format("Succeeded: %s req/sec", successCount.get()));
            info(String.format("   Failed: %s req/sec", failureCount.get()));
            successCount.set(0);
            failureCount.set(0);
        }, 1, 1, TimeUnit.SECONDS);


        if(number > 0) {
            latch.await();
            info(String.format("Succeeded: %s req/sec", successCount.get()));
            info(String.format("   Failed: %s req/sec", failureCount.get()));
            workers.shutdown();
            scheduler.shutdown();
            workers.awaitTermination(1, TimeUnit.MINUTES);
            scheduler.awaitTermination(1, TimeUnit.MINUTES);
        }
    }

    private String readArgValue(String arg, String key) {
        if (arg.startsWith(key))
            return arg.replace(key, "");

        return null;
    }

    private void info(String msg) {
        System.out.println(String.format("%s INFO  %s", LocalDateTime.now(), msg));
    }

    private void err(String msg) {
        System.err.println(String.format("%s ERROR %s", LocalDateTime.now(), msg));
    }

    private void assertNotEmpty(String arg, String errorMessage) {
        if (arg == null || arg.length() == 0) {
            err(errorMessage);
            System.exit(1);
        }

    }
}
