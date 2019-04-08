package cn.keepbx.jpom.controller;

import cn.hutool.core.lang.Validator;
import cn.hutool.core.util.StrUtil;
import cn.jiangzeyin.common.DefaultSystemLog;
import cn.jiangzeyin.common.JsonMessage;
import cn.jiangzeyin.common.spring.SpringUtil;
import cn.keepbx.jpom.common.BaseController;
import cn.keepbx.jpom.common.interceptor.LoginInterceptor;
import cn.keepbx.jpom.common.interceptor.NotLogin;
import cn.keepbx.jpom.controller.system.WhitelistDirectoryController;
import cn.keepbx.jpom.model.UserModel;
import cn.keepbx.jpom.service.system.WhitelistDirectoryService;
import cn.keepbx.jpom.service.user.UserService;
import com.alibaba.fastjson.JSONArray;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;

import javax.annotation.Resource;

/**
 * 初始化程序
 *
 * @author jiangzeyin
 * @date 2019/2/22
 */
@Controller
public class InstallController extends BaseController {
    @Resource
    private UserService userService;
    @Resource
    private WhitelistDirectoryService whitelistDirectoryService;

    @RequestMapping(value = "install.html", method = RequestMethod.GET, produces = MediaType.TEXT_HTML_VALUE)
    @NotLogin
    public String install() {
        if (userService.userListEmpty()) {
            // 判断是否需要填写白名单
            JSONArray jsonArray = whitelistDirectoryService.getProjectDirectory();
            if (jsonArray == null || jsonArray.isEmpty()) {
                setAttribute("whitelist", true);
            }
            return "install";
        }
        // 已存在用户跳转到首页
        return "redirect:index.html";
    }

    /**
     * 初始化提交
     *
     * @param userName           系统管理员登录名
     * @param userPwd            系统管理员的登录密码
     * @param whitelistDirectory 默认白名单目录
     * @return json
     */
    @RequestMapping(value = "install_submit.json", method = RequestMethod.POST, produces = MediaType.APPLICATION_JSON_UTF8_VALUE)
    @NotLogin
    @ResponseBody
    public String installSubmit(String userName, String userPwd, String whitelistDirectory) {
        if (!userService.userListEmpty()) {
            return JsonMessage.getString(100, "系统已经初始化过啦，请勿重复初始化");
        }
        if (StrUtil.isEmpty(userName)) {
            return JsonMessage.getString(400, "登录名不能为空");
        }
        if (userName.length() < UserModel.USER_NAME_MIN_LEN) {
            return JsonMessage.getString(400, "登录名长度必须不小于" + UserModel.USER_NAME_MIN_LEN);
        }
        if (Validator.isChinese(userName) || !checkPathSafe(userName)) {
            return JsonMessage.getString(400, "登录名不能包含汉字并且不能包含特殊字符");
        }
        if (StrUtil.isEmpty(userPwd)) {
            return JsonMessage.getString(400, "密码不能为空");
        }
        if (UserModel.SYSTEM_OCCUPY_NAME.equals(userName) || UserModel.SYSTEM_ADMIN.equals(userName)) {
            return JsonMessage.getString(401, "当前登录名已经被系统占用");
        }
//        // 判断密码级别
//        if (CheckPassword.checkPassword(userPwd) != 2) {
//            return JsonMessage.getString(401, "系统管理员密码强度太低,请使用复杂的密码");
//        }
        //
        UserModel userModel = new UserModel();
        userModel.setName(UserModel.SYSTEM_OCCUPY_NAME);
        userModel.setId(userName);
        userModel.setPassword(userPwd);
        userModel.setParent(UserModel.SYSTEM_ADMIN);
        userModel.setManage(true);
        try {
            userService.addItem(userModel);
        } catch (Exception e) {
            DefaultSystemLog.ERROR().error(e.getMessage(), e);
            return JsonMessage.getString(400, "初始化失败");
        }
        JSONArray jsonArray = whitelistDirectoryService.getProjectDirectory();
        if (jsonArray == null || jsonArray.isEmpty()) {
            // 白名单
            WhitelistDirectoryController whitelistDirectoryController = SpringUtil.getBean(WhitelistDirectoryController.class);
            JsonMessage jsonMessage = whitelistDirectoryController.save(whitelistDirectory, null, null);
            if (jsonMessage.getCode() != 200) {
                return jsonMessage.toString();
            }
        }
        // 自动登录
        setSessionAttribute(LoginInterceptor.SESSION_NAME, userModel);
        return JsonMessage.getString(200, "初始化成功");
    }
}
