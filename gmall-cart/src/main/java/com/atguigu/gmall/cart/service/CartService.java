package com.atguigu.gmall.cart.service;

import com.alibaba.fastjson.JSON;
import com.alibaba.nacos.client.naming.utils.CollectionUtils;
import com.atguigu.gmall.cart.feign.GmallPmsClient;
import com.atguigu.gmall.cart.feign.GmallSmsClient;
import com.atguigu.gmall.cart.feign.GmallWmsClient;
import com.atguigu.gmall.cart.interceptor.LoginInterceptor;
import com.atguigu.gmall.cart.mapper.CartMapper;
import com.atguigu.gmall.cart.pojo.Cart;
import com.atguigu.gmall.cart.pojo.UserInfo;
import com.atguigu.gmall.common.bean.ResponseVo;
import com.atguigu.gmall.pms.entity.SkuAttrValueEntity;
import com.atguigu.gmall.pms.entity.SkuEntity;
import com.atguigu.gmall.sms.vo.ItemSaleVo;
import com.atguigu.gmall.wms.entity.WareSkuEntity;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.BoundHashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class CartService {
    @Autowired
    private GmallPmsClient pmsClient;
    @Autowired
    private GmallSmsClient smsClient;
    @Autowired
    private GmallWmsClient wmsClient;
    @Autowired
    private CartMapper cartMapper;
    @Autowired
    private CartAsyncService cartAsyncService;
    private static final String KEY_PREFIX = "cart:info:";
    private static final String PRICE_PREFIX = "cart:price:";
    @Autowired
    private StringRedisTemplate redisTemplate;

    public void addCart(Cart cart) {
        // 获取登录信息
        String userId = getUserId();
        //外层map的key
        String key = KEY_PREFIX + userId;
        //通过userId或者userKey获取该用户的购物车，这个hashOps相当于内层的map一样
        BoundHashOperations<String, Object, Object> hashOps = this.redisTemplate.boundHashOps(key);
        String skuIdString = cart.getSkuId().toString();
        BigDecimal count = cart.getCount();
        //判断内层的map是否包含了该商品的skuId,所有的泛型都是string
        if (hashOps.hasKey(skuIdString)) {
            //包含则更新数量
            String cartJson = hashOps.get(skuIdString).toString();
            cart = JSON.parseObject(cartJson, Cart.class);
            cart.setCount(cart.getCount().add(count));
            //更新到Mysql
            this.cartAsyncService.updateCart(userId, cart);
        } else {
            // 不包含则给内层的map新增一条记录
            cart.setUserId(userId);
            cart.setCheck(true);
            //查询sku
            ResponseVo<SkuEntity> skuEntityResponseVo = this.pmsClient.querySkuById(cart.getSkuId());
            SkuEntity skuEntity = skuEntityResponseVo.getData();
            if (skuEntity != null) {
                cart.setTitle(skuEntity.getTitle());
                cart.setPrice(skuEntity.getPrice());
                cart.setDefaultImage(skuEntity.getDefaultImage());
            }
            //查询库存
            ResponseVo<List<WareSkuEntity>> responseVo = this.wmsClient.queryWareSkuBySkuId(cart.getSkuId());
            List<WareSkuEntity> wareSkuEntities = responseVo.getData();
            if (!CollectionUtils.isEmpty(wareSkuEntities)) {
                cart.setStore(wareSkuEntities.stream().anyMatch(wareSkuEntity -> wareSkuEntity.getStock() - wareSkuEntity.getStockLocked() > 0));
            }
            //查询营销信息
            ResponseVo<List<ItemSaleVo>> listResponseVo = this.smsClient.queryItemSaleSBySkuId(cart.getSkuId());
            cart.setSales(JSON.toJSONString(listResponseVo.getData()));
            //查询销售属性
            ResponseVo<List<SkuAttrValueEntity>> listResponseVo1 = this.pmsClient.querySkuAttrValueBySkuId(cart.getSkuId());
            cart.setSaleAttrs(JSON.toJSONString(listResponseVo1.getData()));
            //新增到mysql
            this.cartAsyncService.insertCart(cart);
            //添加实时价格缓存到redis
            if(skuEntity != null){
                this.redisTemplate.opsForValue().set(PRICE_PREFIX + skuIdString, skuEntity.getPrice().toString());
            }
        }
        hashOps.put(skuIdString, JSON.toJSONString(cart));
    }

    private String getUserId() {
        UserInfo userInfo = LoginInterceptor.getUserInfo();
        // 如果userId存在，则使用userId；如果userId为空，就是用userKey作为userId
        Long userId = userInfo.getUserId();
        if (userId == null) {
            return userInfo.getUserKey();
        }
        return userId.toString();
    }

    /**
     * 加入购物车成功之后，成功信息的回显
     *
     * @param skuId
     * @return
     */
    public Cart queryCartBySkuId(Long skuId) {
        //用户登录信息
        String userId = this.getUserId();
        //内层的map操作
        String key = KEY_PREFIX + userId;
        BoundHashOperations<String, Object, Object> hashOps = this.redisTemplate.boundHashOps(key);
        //判断该用户的购物车中是否存在该商品
        if (hashOps.hasKey(skuId.toString())) {
            String cartJson = hashOps.get(skuId.toString()).toString();
            return JSON.parseObject(cartJson, Cart.class);
        }
        // 制造异常
        throw new RuntimeException("该用户的购物车不包含该商品信息！");
    }

    public List<Cart> queryCarts() {
        //1.获取userkey,查询未登录的购物车
        UserInfo userInfo = LoginInterceptor.getUserInfo();
        String userKey = userInfo.getUserKey();
        String unloginKey = KEY_PREFIX + userKey;
        //获取未登录购物车内层map
        BoundHashOperations<String, Object, Object> unloginHashOps = this.redisTemplate.boundHashOps(unloginKey);
        List<Object> cartJsons = unloginHashOps.values();
        List<Cart> unloginCarts = null;
        if (!CollectionUtils.isEmpty(cartJsons)) {
            unloginCarts = cartJsons.stream().map(cartJson -> {
                Cart cart = JSON.parseObject(cartJson.toString(), Cart.class);
                cart.setCurrentPrice(new BigDecimal(this.redisTemplate.opsForValue().get(PRICE_PREFIX + cart.getSkuId())));
                return cart;
            }).collect(Collectors.toList());
        }
        //2.获取userId,判断是否登录为登录直接返回未登录的购物车
        Long userId = userInfo.getUserId();
        if (userId == null) {
            return unloginCarts;
        }
        //3.判断有没有未登录的购物车有则直接合并
        String loginKey = KEY_PREFIX + userId;
        BoundHashOperations<String, Object, Object> loginHashOps = this.redisTemplate.boundHashOps(loginKey);
        if (!CollectionUtils.isEmpty(unloginCarts)) {
            unloginCarts.forEach(cart -> {
                if (loginHashOps.hasKey(cart.getSkuId().toString())) {
                    //更新数量
                    BigDecimal count = cart.getCount();
                    String cartJson = loginHashOps.get(cart.getSkuId().toString()).toString();
                    cart = JSON.parseObject(cartJson, Cart.class);
                    cart.setCount(cart.getCount().add(count));
                    //写会redis和mysql
                    this.cartAsyncService.updateCart(userId.toString(), cart);
                } else {
                    //新增购物车记录
                    cart.setUserId(userId.toString());
                    cartAsyncService.insertCart(cart);
                }
                loginHashOps.put(cart.getSkuId().toString(), JSON.toJSONString(cart));
            });
        }
        //4.删除未登录的购物车
        this.redisTemplate.delete(unloginKey);
        this.cartAsyncService.deleteCartByUserId(userKey);
        //5.以userId获取登录状态的购物车
        List<Object> loginCartJsons = loginHashOps.values();
        if (!CollectionUtils.isEmpty(loginCartJsons)) {
            return loginCartJsons.stream().map(cartJson -> {
                Cart cart = JSON.parseObject(cartJson.toString(), Cart.class);
                cart.setCurrentPrice(new BigDecimal(this.redisTemplate.opsForValue().get(PRICE_PREFIX + cart.getSkuId())));
            return cart;
            }).collect(Collectors.toList());
        }

        return null;
    }

    public void updateNum(Cart cart) {
        //获取用户的登录信息
        String userId = this.getUserId();
        String key = KEY_PREFIX + userId;
        BoundHashOperations<String, Object, Object> hashOps = this.redisTemplate.boundHashOps(key);
        if (hashOps.hasKey(cart.getSkuId().toString())) {
            BigDecimal count = cart.getCount();
            String cartJson = hashOps.get(cart.getSkuId().toString()).toString();
            cart = JSON.parseObject(cartJson, Cart.class);
            cart.setCount(count);

            // 写回数据库
            hashOps.put(cart.getSkuId().toString(), JSON.toJSONString(cart));
            this.cartAsyncService.updateCart(userId, cart);
        }

    }

    public void deleteCartBySkuId(Long skuId) {
        String userId = this.getUserId();
        BoundHashOperations<String, Object, Object> hashOps = this.redisTemplate.boundHashOps(KEY_PREFIX + userId);
        if (hashOps.hasKey(skuId.toString())) {
            hashOps.delete(skuId.toString());

            this.cartAsyncService.deleteCartByUserIdAndSkuId(userId, skuId);
        }
    }
}