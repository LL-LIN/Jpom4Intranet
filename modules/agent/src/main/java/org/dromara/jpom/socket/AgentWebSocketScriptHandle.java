/*
 * Copyright (c) 2019 Of Him Code Technology Studio
 * Jpom is licensed under Mulan PSL v2.
 * You can use this software according to the terms and conditions of the Mulan PSL v2.
 * You may obtain a copy of Mulan PSL v2 at:
 * 			http://license.coscl.org.cn/MulanPSL2
 * THIS SOFTWARE IS PROVIDED ON AN "AS IS" BASIS, WITHOUT WARRANTIES OF ANY KIND, EITHER EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO NON-INFRINGEMENT, MERCHANTABILITY OR FIT FOR A PARTICULAR PURPOSE.
 * See the Mulan PSL v2 for more details.
 */
package org.dromara.jpom.socket;

import cn.hutool.core.util.StrUtil;
import cn.keepbx.jpom.model.JsonMessage;
import com.alibaba.fastjson2.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.dromara.jpom.configuration.AgentConfig;
import org.dromara.jpom.model.data.NodeScriptModel;
import org.dromara.jpom.script.NodeScriptProcessBuilder;
import org.dromara.jpom.service.script.NodeScriptServer;
import org.dromara.jpom.util.SocketSessionUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.websocket.*;
import javax.websocket.server.ServerEndpoint;
import java.io.IOException;

/**
 * 脚本模板socket
 *
 * @author bwcx_jzy
 * @since 2019/4/24
 */
@ServerEndpoint(value = "/script_run")
@Component
@Slf4j
public class AgentWebSocketScriptHandle extends BaseAgentWebSocketHandle {

    private static NodeScriptServer nodeScriptServer;

    @Autowired
    public void init(NodeScriptServer nodeScriptServer, AgentConfig agentConfig) {
        AgentWebSocketScriptHandle.nodeScriptServer = nodeScriptServer;
        setAgentAuthorize(agentConfig.getAuthorize());
    }

    @OnOpen
    public void onOpen(Session session) {
        try {
            if (super.checkAuthorize(session)) {
                return;
            }
            String id = this.getParameters(session, "id");
            String workspaceId = this.getParameters(session, "workspaceId");
            if (StrUtil.hasEmpty(id, workspaceId)) {
                SocketSessionUtil.send(session, "脚本模板或者工作空间未知");
                return;
            }

            NodeScriptModel nodeScriptModel = nodeScriptServer.getItem(id);
            if (nodeScriptModel == null) {
                SocketSessionUtil.send(session, "没有找到对应的脚本模板");
                return;
            }
            SocketSessionUtil.send(session, "连接成功：" + nodeScriptModel.getName());
        } catch (Exception e) {
            log.error("socket 错误", e);
            try {
                SocketSessionUtil.send(session, JsonMessage.getString(500, "系统错误!"));
                session.close();
            } catch (IOException e1) {
                log.error(e1.getMessage(), e1);
            }
        }
    }

    @OnMessage
    public void onMessage(String message, Session session) throws Exception {
        JSONObject json = JSONObject.parseObject(message);
        String scriptId = json.getString("scriptId");
        NodeScriptModel nodeScriptModel = nodeScriptServer.getItem(scriptId);
        if (nodeScriptModel == null) {
            SocketSessionUtil.send(session, "没有对应脚本模板:" + scriptId);
            session.close();
            return;
        }
        String op = json.getString("op");
        ConsoleCommandOp consoleCommandOp = ConsoleCommandOp.valueOf(op);
        switch (consoleCommandOp) {
            case start: {
                String args = json.getString("args");
                String executeId = json.getString("executeId");
                if (StrUtil.isEmpty(executeId)) {
                    SocketSessionUtil.send(session, "没有执行ID");
                    session.close();
                    return;
                }
                NodeScriptProcessBuilder.addWatcher(nodeScriptModel, executeId, args, session);
                break;
            }
            case stop: {
                String executeId = json.getString("executeId");
                if (StrUtil.isEmpty(executeId)) {
                    SocketSessionUtil.send(session, "没有执行ID");
                    session.close();
                    return;
                }
                NodeScriptProcessBuilder.stopRun(executeId);
                break;
            }
            case heart:
            default:
                return;
        }
        // 记录操作人
        nodeScriptModel = nodeScriptServer.getItem(scriptId);
        String name = getOptUserName(session);
        nodeScriptModel.setLastRunUser(name);
        nodeScriptServer.updateItem(nodeScriptModel);
        json.put("code", 200);
        String value = "执行成功";
        json.put("msg", value);
        log.debug(json.toString());
        SocketSessionUtil.send(session, json.toString());
    }


    @Override
    @OnClose
    public void onClose(Session session, CloseReason closeReason) {
        super.onClose(session, closeReason);
        NodeScriptProcessBuilder.stopWatcher(session);
    }

    @OnError
    @Override
    public void onError(Session session, Throwable thr) {
        super.onError(session, thr);
    }
}
