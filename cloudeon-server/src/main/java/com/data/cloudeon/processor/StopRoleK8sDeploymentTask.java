package com.data.cloudeon.processor;

import cn.hutool.extra.spring.SpringUtil;
import com.data.cloudeon.config.CloudeonConfigProp;
import com.data.cloudeon.dao.ServiceInstanceRepository;
import com.data.cloudeon.dao.ServiceRoleInstanceRepository;
import com.data.cloudeon.dao.StackServiceRoleRepository;
import com.data.cloudeon.entity.ServiceInstanceEntity;
import com.data.cloudeon.entity.ServiceRoleInstanceEntity;
import com.data.cloudeon.entity.StackServiceRoleEntity;
import com.data.cloudeon.service.KubeService;
import com.data.cloudeon.utils.Constant;
import com.data.cloudeon.enums.ServiceRoleState;
import io.fabric8.kubernetes.client.KubernetesClient;
import lombok.NoArgsConstructor;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.util.List;

/**
 * 为角色实例删除k8s deployment
 */
@NoArgsConstructor
public class StopRoleK8sDeploymentTask extends BaseCloudeonTask {
    @Override
    public void internalExecute() {
        ServiceInstanceRepository serviceInstanceRepository = SpringUtil.getBean(ServiceInstanceRepository.class);
        StackServiceRoleRepository stackServiceRoleRepository = SpringUtil.getBean(StackServiceRoleRepository.class);
        ServiceRoleInstanceRepository serviceRoleInstanceRepository = SpringUtil.getBean(ServiceRoleInstanceRepository.class);
        KubeService kubeService = SpringUtil.getBean(KubeService.class);


        CloudeonConfigProp cloudeonConfigProp = SpringUtil.getBean(CloudeonConfigProp.class);
        String workHome = cloudeonConfigProp.getWorkHome();

        // 获取服务实例信息
        Integer serviceInstanceId = taskParam.getServiceInstanceId();
        ServiceInstanceEntity serviceInstanceEntity = serviceInstanceRepository.findById(serviceInstanceId).get();

        // 查询框架服务角色信息
        String roleName = taskParam.getRoleName();
        StackServiceRoleEntity stackServiceRoleEntity = stackServiceRoleRepository.findByServiceIdAndName(taskParam.getStackServiceId(), roleName);
        String roleFullName = stackServiceRoleEntity.getRoleFullName();

        // 读取本地k8s资源工作目录  ${workHome}/k8s-resource/ZOOKEEPER1/
        String k8sResourceDirPath = workHome + File.separator + Constant.K8S_RESOURCE_DIR + File.separator + serviceInstanceEntity.getServiceName();
        String k8sServiceResourceFilePath = k8sResourceDirPath + File.separator + roleFullName + ".yaml";

        // 判断k8s资源文件是否存在
        if (new File(k8sServiceResourceFilePath).exists()) {
            log.info("在k8s上停止deployment ,使用本地资源文件: {}", k8sServiceResourceFilePath);
            try (KubernetesClient client = kubeService.getKubeClient(serviceInstanceEntity.getClusterId());) {
                client.load(new FileInputStream(k8sServiceResourceFilePath))
                        .inNamespace("default")
                        .delete();
            } catch (FileNotFoundException e) {
                e.printStackTrace();
                throw new RuntimeException(e);
            }

            // 更新角色实例状态为已停止
            List<ServiceRoleInstanceEntity> roleInstanceEntities = serviceRoleInstanceRepository.findByServiceInstanceIdAndServiceRoleName(serviceInstanceId, stackServiceRoleEntity.getName());
            roleInstanceEntities.forEach(r->{
                r.setServiceRoleState(ServiceRoleState.ROLE_STOPPED);
                serviceRoleInstanceRepository.save(r);
            });

        }
    }
}
