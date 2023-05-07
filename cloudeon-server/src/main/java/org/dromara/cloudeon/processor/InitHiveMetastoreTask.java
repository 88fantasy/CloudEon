/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package org.dromara.cloudeon.processor;

import cn.hutool.core.util.StrUtil;
import cn.hutool.db.ds.simple.SimpleDataSource;
import cn.hutool.db.handler.StringHandler;
import cn.hutool.db.sql.SqlExecutor;
import cn.hutool.extra.spring.SpringUtil;
import com.jcraft.jsch.Session;
import lombok.NoArgsConstructor;
import org.apache.sshd.client.session.ClientSession;
import org.dromara.cloudeon.dao.*;
import org.dromara.cloudeon.entity.ClusterNodeEntity;
import org.dromara.cloudeon.entity.ServiceInstanceEntity;
import org.dromara.cloudeon.entity.ServiceRoleInstanceEntity;
import org.dromara.cloudeon.entity.StackServiceEntity;
import org.dromara.cloudeon.service.SshPoolService;
import org.dromara.cloudeon.utils.JschUtils;
import org.dromara.cloudeon.utils.SshUtils;

import javax.sql.DataSource;
import java.io.IOException;
import java.nio.charset.Charset;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

import static org.dromara.cloudeon.utils.Constant.DEFAULT_JSCH_TIMEOUT;

@NoArgsConstructor
public class InitHiveMetastoreTask extends BaseCloudeonTask {


    @Override
    public void internalExecute() {
        ServiceInstanceRepository serviceInstanceRepository = SpringUtil.getBean(ServiceInstanceRepository.class);
        StackServiceRepository stackServiceRepository = SpringUtil.getBean(StackServiceRepository.class);
        ServiceRoleInstanceRepository roleInstanceRepository = SpringUtil.getBean(ServiceRoleInstanceRepository.class);
        ClusterNodeRepository clusterNodeRepository = SpringUtil.getBean(ClusterNodeRepository.class);
        ServiceInstanceConfigRepository configRepository = SpringUtil.getBean(ServiceInstanceConfigRepository.class);
        SshPoolService sshPoolService = SpringUtil.getBean(SshPoolService.class);


        TaskParam taskParam = getTaskParam();
        Integer serviceInstanceId = taskParam.getServiceInstanceId();

        ServiceInstanceEntity serviceInstanceEntity = serviceInstanceRepository.findById(serviceInstanceId).get();
        StackServiceEntity stackServiceEntity = stackServiceRepository.findById(serviceInstanceEntity.getStackServiceId()).get();
        String serviceName = serviceInstanceEntity.getServiceName();
        // 校验metastore里的version和服务的version是否一致
        String username = configRepository.findByServiceInstanceIdAndName(serviceInstanceId, "javax.jdo.option.ConnectionUserName").getValue();
        String password = configRepository.findByServiceInstanceIdAndName(serviceInstanceId, "javax.jdo.option.ConnectionPassword").getValue();
        String url = configRepository.findByServiceInstanceIdAndName(serviceInstanceId, "javax.jdo.option.ConnectionURL").getValue();
        String substringUrl = url.substring(0, url.indexOf("?"));
        DataSource ds = new SimpleDataSource(substringUrl, username, password);
        String qureyResult = "";
        try (Connection conn = ds.getConnection();) {
            String sql = " select SCHEMA_VERSION from VERSION";
            log.info("执行sql: {} 检查hive元数据库是否已经初始化", sql);
            qureyResult = SqlExecutor.query(conn, sql, new StringHandler());

        } catch (SQLException e) {
            // 不存在VERSION表代表没初始化过，是正常的
            if (e.getMessage().contains("doesn't exist")) {
                log.info("检查到hive元数据库没有初始化...");
            } else {
                // 有可能连接异常
                throw new RuntimeException(e);
            }
        }
        if (StrUtil.isNotBlank(qureyResult)) {
            log.info("检查到hive元数据库已经初始化过，无需执行初始化脚本...");
        } else {
            // todo 能捕获到执行日志吗？
            String cmd = String.format("sudo docker  run --net=host -v /opt/edp/%s/conf:/opt/edp/%s/conf  -v /opt/edp/%s/log:/opt/edp/%s/log  %s sh -c \"  /opt/edp/%s/conf/init-metastore-db.sh \"   ",
                    serviceName, serviceName, serviceName, serviceName, stackServiceEntity.getDockerImage(), serviceName);

            // 选择metastore所在节点执行
            List<ServiceRoleInstanceEntity> roleInstanceEntities = roleInstanceRepository.findByServiceInstanceIdAndServiceRoleName(serviceInstanceId, "HIVE_SERVER2");
            ServiceRoleInstanceEntity firstNamenode = roleInstanceEntities.get(0);
            Integer nodeId = firstNamenode.getNodeId();
            ClusterNodeEntity nodeEntity = clusterNodeRepository.findById(nodeId).get();
            String ip = nodeEntity.getIp();
            log.info("在节点" + ip + "上执行命令:" + cmd);
            Session clientSession = sshPoolService.openSession(ip, nodeEntity.getSshPort(), nodeEntity.getSshUser(), nodeEntity.getSshPassword());
            try {
                JschUtils.execCallbackLine(clientSession, Charset.defaultCharset(), DEFAULT_JSCH_TIMEOUT,cmd ,null,remoteSshTaskLineHandler,remoteSshTaskErrorLineHandler );

            } catch (IOException e) {
                e.printStackTrace();
                throw new RuntimeException(e);
            }

        }
    }
}
