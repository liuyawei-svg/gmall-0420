package com.atguigu.gmall.ums.service.impl;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.UUID;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.atguigu.gmall.common.bean.PageResultVo;
import com.atguigu.gmall.common.bean.PageParamVo;
import org.apache.commons.codec.digest.DigestUtils;
import com.atguigu.gmall.ums.mapper.UserMapper;
import com.atguigu.gmall.ums.entity.UserEntity;
import com.atguigu.gmall.ums.service.UserService;
import org.springframework.util.StringUtils;


@Service("userService")
public class UserServiceImpl extends ServiceImpl<UserMapper, UserEntity> implements UserService {
    @Autowired
    private UserMapper userMapper;

    @Override
    public PageResultVo queryPage(PageParamVo paramVo) {
        IPage<UserEntity> page = this.page(
                paramVo.getPage(),
                new QueryWrapper<UserEntity>()
        );

        return new PageResultVo(page);
    }

    @Override
    public Boolean checkData(String data, Integer type) {
        QueryWrapper<UserEntity> wrapper = new QueryWrapper<>();
        switch (type) {
            case 1:
                wrapper.eq("username", data);
                break;
            case 2:
                wrapper.eq("phone", data);
                break;
            case 3:
                wrapper.eq("email", data);
                break;
            default:
                return null;
        }
        return this.userMapper.selectCount(wrapper) == 0;
    }

    @Override
    public void register(UserEntity userEntity, String code) {

        //1.TODO查询redis中的验证码，校验验证码
        //2.生成随机码作为salt
        String salt = UUID.randomUUID().toString().substring(0, 6);
        userEntity.setSalt(salt);
        //3.对密码加盐加密
        userEntity.setPassword(DigestUtils.md5Hex(userEntity.getPassword() + salt));
        //4.新增用户信息
        userEntity.setLevelId(1l);
        userEntity.setStatus(1);
        userEntity.setCreateTime(new Date());
        userEntity.setSourceType(1);
        userEntity.setIntegration(1000);
        userEntity.setGrowth(1000);
        this.save(userEntity);
    }

    @Override
    public UserEntity queryUser(String loginName, String password) {
        //1.根据登录名查询用户信息
        UserEntity userEntity = this.getOne(new QueryWrapper<UserEntity>()
                .eq("username", loginName)
                .or().eq("email", loginName)
                .or().eq("phone", loginName));
        //2.判断用户是否存在
        if (userEntity == null) {
            return null;
        }
        //3.获取用户信息中的盐，对用户输入明文密码加盐加密
        String salt = userEntity.getSalt();
        password = DigestUtils.md5Hex(password + salt);
        //4.拿数据库中的密码和上一步加盐加密后的密码比较
        String dbPassword = userEntity.getPassword();
        if(!StringUtils.pathEquals(password,dbPassword)){
            return null;
        }

        return userEntity;
    }

}