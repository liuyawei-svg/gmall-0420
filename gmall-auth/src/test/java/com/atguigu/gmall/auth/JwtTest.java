package com.atguigu.gmall.auth;

import com.atguigu.gmall.common.utils.JwtUtils;
import com.atguigu.gmall.common.utils.RsaUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.HashMap;
import java.util.Map;

public class JwtTest {

    // 别忘了创建D:\\project\rsa目录
	private static final String pubKeyPath = "D:\\java\\project-0420\\rsa\\rsa.pub";
    private static final String priKeyPath = "D:\\java\\project-0420\\rsa\\rsa.pri";

    private PublicKey publicKey;

    private PrivateKey privateKey;

    @Test
    public void testRsa() throws Exception {
        RsaUtils.generateKey(pubKeyPath, priKeyPath, "ab23RED%$&#@");
    }

    @BeforeEach
    public void testGetRsa() throws Exception {
        this.publicKey = RsaUtils.getPublicKey(pubKeyPath);
        this.privateKey = RsaUtils.getPrivateKey(priKeyPath);
    }

    @Test
    public void testGenerateToken() throws Exception {
        Map<String, Object> map = new HashMap<>();
        map.put("id", "11");
        map.put("username", "liuyan");
        // 生成token
        String token = JwtUtils.generateToken(map, privateKey, 5);
        System.out.println("token = " + token);
    }

    @Test
    public void testParseToken() throws Exception {
        String token = "eyJhbGciOiJSUzI1NiJ9.eyJpZCI6IjExIiwidXNlcm5hbWUiOiJsaXV5YW4iLCJleHAiOjE2MDI2NzgyMDl9.EDKs9z_D0AZ79SqVv798FUQp72khOUKKFHwdol4jO9VwVA_eIqyW8U2aZ6BpauINQJ9-bDG4vgZEuS6cpXfetvMUeslemyI93sWz48eh2TIew5vzo5ypZaVKeiymhG0QPjT0FxwJVn2CCW1JH8OTAyEYat4K4Q0mxTNhAxSE9Rm322rF4CTFGbY9kFiUProX4wBBTqxrhBOwJCEETsll1Gvo2IhsibaFzQPNXCjxyAKBdk3HhpZTzwPOXe8hGu9T5HCdx858qi1W8vbUJ73Px_TwDztls1o9r5m20thuAr3rK1mo16z645UGhLRz27QAqW6RpruCniqfEM4a3bDwRg";

        // 解析token
        Map<String, Object> map = JwtUtils.getInfoFromToken(token, publicKey);
        System.out.println("id: " + map.get("id"));
        System.out.println("userName: " + map.get("username"));
    }
}