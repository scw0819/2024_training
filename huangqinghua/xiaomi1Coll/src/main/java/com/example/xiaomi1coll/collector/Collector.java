package com.example.xiaomi1coll.collector;

import com.example.xiaomi1coll.entity.Metric;
import com.sun.management.OperatingSystemMXBean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.lang.management.ManagementFactory;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
@Component
public class Collector {
    private static final Logger logger = LoggerFactory.getLogger(Collector.class);
    private final RestTemplate restTemplate;
    private final OperatingSystemMXBean osBean;
    private final ExecutorService executorService;

    public Collector() {
        this.restTemplate = new RestTemplate();
        this.osBean = (OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();
        // 创建一个包含4个线程的线程池
        this.executorService = Executors.newFixedThreadPool(4);
    }

    @Scheduled(fixedRate = 60000) // 每分钟执行一次
    // @Scheduled(fixedRate = 3000) // 测试，3秒一次
    public void collectAndReport() {
        double cpuLoad = getCpuUsage(); // 获取系统的平均负载（过去1分钟）
        double memoryUsage = getMemoryUsage(); // 获取内存使用情况

        Metric cpuMetric = createMetric("cpu.used.percent", cpuLoad);
        Metric memMetric = createMetric("mem.used.percent", memoryUsage);

        // 提交任务到线程池
        submitMetric(cpuMetric);
        submitMetric(memMetric);

        System.out.println("cpu采集日志："+cpuMetric);
        System.out.println("mem采集日志："+memMetric);

    }

    // 创建主机指标类
    private Metric createMetric(String metricName, double value) {
        Metric metric = new Metric();
        metric.setMetric(metricName);
        metric.setEndPoint("my-computer");
        metric.setTimeStamp(Instant.now().getEpochSecond());
        metric.setStep(60);
        metric.setValue(value);
        return metric;
    }

    // 线程调用
    private void submitMetric(Metric metric) {
        // 提交任务到执行服务
        executorService.submit(() -> sendMetric(metric));
    }

    // 上报数据
    private void sendMetric(Metric metric) {
        // 构造HTTP请求
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Metric> requestEntity = new HttpEntity<>(metric, headers);

        try {
            // 发送POST请求到服务器的API接口
            ResponseEntity<String> response = restTemplate.exchange("http://localhost:8080/api/metric/upload",
                    HttpMethod.POST, requestEntity, String.class);

            // 记录响应的状态码和内容
            logger.info("Metric sent: {}", metric);
            logger.info("Response Status: {}", response.getStatusCode());
            logger.info("Response Body: {}", response.getBody());
        }catch (Exception e) {
            logger.error("Error sending metric: {}", metric, e);
        }

    }

    // 获取主机内存使用率
    private double getMemoryUsage() {
        long totalMemory = osBean.getTotalMemorySize();
        long freeMemory = osBean.getFreeMemorySize();
        return (double) (totalMemory - freeMemory) / totalMemory * 100;
    }

    // 获取主机cpu使用率
    private double getCpuUsage() {
        double systemCpuLoad = osBean.getCpuLoad();
        if (systemCpuLoad < 0) {
            // 如果返回负值，说明不支持或无法获取CPU负载
            return Double.NaN;
        }
        return twoDecimal(systemCpuLoad * 100); // 转换为百分比并保留两位小数
    }

    // 控制精度
    public double twoDecimal(double doubleValue) {
        BigDecimal bigDecimal = new BigDecimal(doubleValue).setScale(2, RoundingMode.HALF_UP);
        return bigDecimal.doubleValue();
    }
}
