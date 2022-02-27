package com.alibaba.otter.canal.prometheus;

import com.alibaba.otter.canal.instance.core.CanalInstance;
import com.alibaba.otter.canal.prometheus.impl.PrometheusClientInstanceProfiler;
import com.alibaba.otter.canal.server.netty.ClientInstanceProfiler;
import com.alibaba.otter.canal.spi.CanalMetricsService;
import com.sun.net.httpserver.HttpServer;
import io.prometheus.client.exporter.HTTPServer;
import io.prometheus.client.hotspot.DefaultExports;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.ReflectionUtils;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;

import static com.alibaba.otter.canal.server.netty.CanalServerWithNettyProfiler.NOP;
import static com.alibaba.otter.canal.server.netty.CanalServerWithNettyProfiler.profiler;

/**
 * @author Chuanyi Li
 */
public class PrometheusService implements CanalMetricsService {

    private static final Logger logger = LoggerFactory.getLogger(PrometheusService.class);
    private final CanalInstanceExports instanceExports;
    private volatile boolean running = false;
    private int port;
    private HTTPServer server;
    private final ClientInstanceProfiler clientProfiler;

    private PrometheusService() {
        this.instanceExports = CanalInstanceExports.instance();
        this.clientProfiler = PrometheusClientInstanceProfiler.instance();
    }

    private static class SingletonHolder {
        private static final PrometheusService SINGLETON = new PrometheusService();
    }

    public static PrometheusService getInstance() {
        return SingletonHolder.SINGLETON;
    }

    @Override
    public void initialize() {
        try {
            logger.info("Start prometheus HTTPServer on port {}.", port);
            //TODO 2.Https?
            server = new HTTPServer(port);
            addHealthCheckApi(server);

        } catch (IOException e) {
            logger.warn("Unable to start prometheus HTTPServer.", e);
            return;
        }
        try {
            // JVM exports
            DefaultExports.initialize();
            instanceExports.initialize();
            if (!clientProfiler.isStart()) {
                clientProfiler.start();
            }
            profiler().setInstanceProfiler(clientProfiler);
        } catch (Throwable t) {
            logger.warn("Unable to initialize server exports.", t);
        }

        running = true;
    }

    /**
     * 添加健康检查api
     *
     * @param server
     */
    private void addHealthCheckApi(HTTPServer server) {
        logger.info("add health check api: http://127.0.0.1:{}/health", port);
        try {
            Field serverField = ReflectionUtils.findField(HTTPServer.class, "server");
            ReflectionUtils.makeAccessible(serverField);
            HttpServer httpServer = (HttpServer) ReflectionUtils.getField(serverField, server);
            httpServer.createContext("/health", t -> {
                byte[] text = "{\"status\":200}".getBytes(StandardCharsets.UTF_8);
                t.getResponseHeaders().set("Content-Type", "application/json;charset=UTF-8");
                t.getResponseHeaders().set("Content-Length", String.valueOf(text.length));
                t.sendResponseHeaders(200, text.length);
                t.getResponseBody().write(text);
                t.close();
            });
        } catch (Exception ex) {
            logger.error("addHealthCheckApi err", ex);
        }

    }

    @Override
    public void terminate() {
        running = false;
        try {
            instanceExports.terminate();
            if (clientProfiler.isStart()) {
                clientProfiler.stop();
            }
            profiler().setInstanceProfiler(NOP);
            if (server != null) {
                server.stop();
            }
        } catch (Throwable t) {
            logger.warn("Something happened while terminating.", t);
        }
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    @Override
    public void register(CanalInstance instance) {
        if (instance.isStart()) {
            logger.warn("Cannot register metrics for destination {} that is running.", instance.getDestination());
            return;
        }
        try {
            instanceExports.register(instance);
        } catch (Throwable t) {
            logger.warn("Unable to register instance exports for {}.", instance.getDestination(), t);
        }
        logger.info("Register metrics for destination {}.", instance.getDestination());
    }

    @Override
    public void unregister(CanalInstance instance) {
        if (instance.isStart()) {
            logger.warn("Try unregister metrics after destination {} is stopped.", instance.getDestination());
        }
        try {
            instanceExports.unregister(instance);
        } catch (Throwable t) {
            logger.warn("Unable to unregister instance exports for {}.", instance.getDestination(), t);
        }
        logger.info("Unregister metrics for destination {}.", instance.getDestination());
    }

    @Override
    public void setServerPort(int port) {
        this.port = port;
    }


}
