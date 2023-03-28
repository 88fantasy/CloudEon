package com.data.udh.controller;

import com.alibaba.fastjson.JSONObject;
import com.data.udh.controller.response.ActiveAlertVO;
import com.data.udh.dao.AlertMessageRepository;
import com.data.udh.dao.ClusterNodeRepository;
import com.data.udh.dao.ServiceInstanceRepository;
import com.data.udh.dao.ServiceRoleInstanceRepository;
import com.data.udh.dto.*;
import com.data.udh.entity.AlertMessageEntity;
import com.data.udh.entity.ClusterNodeEntity;
import com.data.udh.entity.ServiceRoleInstanceEntity;
import com.data.udh.enums.AlertLevel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/alert")
public class AlertController {

    @Resource
    private AlertMessageRepository alertMessageRepository;

    @Resource
    private ServiceRoleInstanceRepository roleInstanceRepository;

    @Resource
    private ServiceInstanceRepository serviceInstanceRepository;

    @Resource
    private ClusterNodeRepository clusterNodeRepository;

    /**
     * 接收alertmanager告警回调
     * todo 联动服务角色实例状态和节点状态
     */
    @RequestMapping("/webhook")
    public ResultDTO<Void> save(@RequestBody String alertMessage) {
        // 接收alertmanager告警:
        // {"receiver":"web\\.hook","status":"firing","alerts":[{"status":"firing","labels":{"alertname":"主机CPU使用率","clusterId":"1","instance":"k8s-node1:9101","serviceRoleName":"node","severity":"exception"},"annotations":{"description":"的k8s-node1:9101实例产生告警","summary":"444"},"startsAt":"2023-03-25T14:39:51.68943305+08:00","endsAt":"0001-01-01T00:00:00Z","generatorURL":"http://k8s-node1:9090/graph?g0.expr=%281+-+avg+by%28instance%29+%28irate%28node_cpu_seconds_total%7Bmode%3D%22idle%22%7D%5B5m%5D%29%29%29+%2A+100+%3E+95\u0026g0.tab=1","fingerprint":"72a2002704e27a2e"},{"status":"firing","labels":{"alertname":"主机CPU使用率","clusterId":"1","instance":"k8s-node2:9101","serviceRoleName":"node","severity":"exception"},"annotations":{"description":"的k8s-node2:9101实例产生告警","summary":"444"},"startsAt":"2023-03-25T14:41:21.68943305+08:00","endsAt":"0001-01-01T00:00:00Z","generatorURL":"http://k8s-node1:9090/graph?g0.expr=%281+-+avg+by%28instance%29+%28irate%28node_cpu_seconds_total%7Bmode%3D%22idle%22%7D%5B5m%5D%29%29%29+%2A+100+%3E+95\u0026g0.tab=1","fingerprint":"ba18df1a61fe8e0b"},{"status":"firing","labels":{"alertname":"主机CPU使用率","clusterId":"1","instance":"k8s-node3:9101","serviceRoleName":"node","severity":"exception"},"annotations":{"description":"的k8s-node3:9101实例产生告警","summary":"444"},"startsAt":"2023-03-25T14:41:06.68943305+08:00","endsAt":"0001-01-01T00:00:00Z","generatorURL":"http://k8s-node1:9090/graph?g0.expr=%281+-+avg+by%28instance%29+%28irate%28node_cpu_seconds_total%7Bmode%3D%22idle%22%7D%5B5m%5D%29%29%29+%2A+100+%3E+95\u0026g0.tab=1","fingerprint":"5a6e9db40eb24b04"}],"groupLabels":{"alertname":"主机CPU使用率"},"commonLabels":{"alertname":"主机CPU使用率","clusterId":"1","serviceRoleName":"node","severity":"exception"},"commonAnnotations":{"summary":"444"},"externalURL":"http://k8s-node1:9093","version":"4","groupKey":"{}:{alertname=\"主机CPU使用率\"}","truncatedAlerts":0}
        // {"receiver":"web\\.hook","status":"resolved","alerts":[{"status":"resolved","labels":{"alertLevel":"exception","alertname":"Grafana进程存活","clusterId":"1","instance":"k8s-node1:3000","job":"grafana","serviceRoleName":"Grafana"},"annotations":{"alertAdvice":"Grafana宕机，请重新启动","alertInfo":"grafana的k8s-node1:3000实例产生告警"},"startsAt":"2023-03-25T15:24:36.039625135+08:00","endsAt":"2023-03-25T15:27:51.039625135+08:00","generatorURL":"http://k8s-node1:9090/graph?g0.expr=up%7Bjob%3D%22grafana%22%7D+%21%3D+1\u0026g0.tab=1","fingerprint":"20453f227ff62f33"}],"groupLabels":{"alertname":"Grafana进程存活"},"commonLabels":{"alertLevel":"exception","alertname":"Grafana进程存活","clusterId":"1","instance":"k8s-node1:3000","job":"grafana","serviceRoleName":"Grafana"},"commonAnnotations":{"alertAdvice":"Grafana宕机，请重新启动","alertInfo":"grafana的k8s-node1:3000实例产生告警"},"externalURL":"http://k8s-node1:9093","version":"4","groupKey":"{}:{alertname=\"Grafana进程存活\"}","truncatedAlerts":0}
        AlertMessage alertMes = JSONObject.parseObject(alertMessage, AlertMessage.class);
        log.info("接收alertmanager告警:" + alertMessage);
        // 判断告警状态
        if (alertMes.getStatus().equals("firing")) {
            List<AlertMessageEntity> alertMessageEntities = alertMes.getAlerts().stream().map(new Function<Alert, AlertMessageEntity>() {
                @Override
                public AlertMessageEntity apply(Alert alert) {
                    String startsAt = alert.getStartsAt();
                    AlertLabels labels = alert.getLabels();
                    String alertname = labels.getAlertname();
                    int clusterId = labels.getClusterId();
                    String instance = labels.getInstance();
                    String hostname = instance.split(":")[0];
                    String serviceRoleName = labels.getServiceRoleName();
                    // 查询服务角色实例
                    ServiceRoleInstanceEntity serviceRoleInstanceEntity = roleInstanceRepository.findByServiceRoleNameAndClusterIdAndHostname(clusterId, serviceRoleName, hostname);
                    // 根据节点hostname查询节点id
                    ClusterNodeEntity roleClusterNode = clusterNodeRepository.findByHostname(hostname);
                    String severity = labels.getAlertLevel();
                    AlertLevel alertLevel = AlertLevel.valueOf(severity.toUpperCase());
                    Annotations annotations = alert.getAnnotations();
                    String alertAdvice = annotations.getAlertAdvice();
                    String alertInfo = annotations.getAlertInfo();
                    AlertMessageEntity alertMessageEntity = AlertMessageEntity.builder()
                            .serviceInstanceId(serviceRoleInstanceEntity.getServiceInstanceId())
                            .serviceRoleInstanceId(serviceRoleInstanceEntity.getId())
                            .hostname(hostname)
                            .nodeId(roleClusterNode.getId())
                            .fireTime(startsAt)
                            .createTime(new Date())
                            .alertName(alertname)
                            .alertLevel(alertLevel)
                            .alertAdvice(alertAdvice)
                            .alertInfo(alertInfo)
                            .clusterId(clusterId)
                            .build();
                    return alertMessageEntity;
                }
            }).collect(Collectors.toList());

            alertMessageRepository.saveAll(alertMessageEntities);

        } else if (alertMes.getStatus().equals("resolved")) {

            // 根据开始时间和alertname找回原来的告警，更新endAt和状态
            alertMes.getAlerts().stream().forEach(alert -> {
                String startsAt = alert.getStartsAt();
                AlertLabels labels = alert.getLabels();
                String alertname = labels.getAlertname();

                AlertMessageEntity alertMessageEntity = alertMessageRepository.findByFireTimeAndAlertName(startsAt, alertname);
                if (alertMessageEntity != null) {
                    String endsAt = alert.getEndsAt();
                    alertMessageEntity.setSolveTime(endsAt);
                    alertMessageEntity.setUpdateTime(new Date());
                    alertMessageRepository.save(alertMessageEntity);
                }

            });
        }
        return ResultDTO.success(null);
    }


    @GetMapping("/active")
    public ResultDTO<List<ActiveAlertVO>> getActiveMessage() {
        List<ActiveAlertVO> activeAlertVOS = alertMessageRepository.findByIsResolve(false).stream().map(new Function<AlertMessageEntity, ActiveAlertVO>() {
            @Override
            public ActiveAlertVO apply(AlertMessageEntity alertMessageEntity) {
                Integer serviceInstanceId = alertMessageEntity.getServiceInstanceId();
                Integer roleInstanceId = alertMessageEntity.getServiceRoleInstanceId();

                String serviceLabel = serviceInstanceRepository.findById(serviceInstanceId).get().getLabel();
                String roleInstanceLabel = roleInstanceRepository.getRoleInstanceLabel(roleInstanceId);

                return ActiveAlertVO.builder()
                        .alertId(alertMessageEntity.getId())
                        .advice(alertMessageEntity.getAlertAdvice())
                        .alertLevelMsg(alertMessageEntity.getAlertLevel().getDesc())
                        .alertName(alertMessageEntity.getAlertName())
                        .createTime(alertMessageEntity.getCreateTime())
                        .info(alertMessageEntity.getAlertInfo())
                        .serviceInstanceName(serviceLabel)
                        .serviceRoleLabel(roleInstanceLabel)
                        .serviceInstanceId(serviceInstanceId)
                        .hostname(alertMessageEntity.getHostname())
                        .serviceRoleInstanceId(roleInstanceId)
                        .build();
            }
        }).collect(Collectors.toList());

        return ResultDTO.success(activeAlertVOS) ;
    }

}
