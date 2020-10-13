package com.atguigu.gmall.index.service;

import com.alibaba.fastjson.JSON;
import com.atguigu.gmall.common.bean.ResponseVo;
import com.atguigu.gmall.index.aspect.GmallCache;
import com.atguigu.gmall.index.feign.GmallPmsClient;
import com.atguigu.gmall.index.utils.DistributedLock;
import com.atguigu.gmall.pms.entity.CategoryEntity;
import org.apache.commons.lang3.StringUtils;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import javax.swing.plaf.TableHeaderUI;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
public class IndexService {
    @Autowired
    private GmallPmsClient pmsClient;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private DistributedLock distributedLock;

    @Autowired
    private RedissonClient redissonClient;

    private static final String KEY_PREFIX = "index:cate:";

    public List<CategoryEntity> queryLvl1Categories() {
        ResponseVo<List<CategoryEntity>> responseVo = this.pmsClient.queryCategory(0l);
        return responseVo.getData();
    }
    @GmallCache(prefix = KEY_PREFIX, lock = "lock:", timeout = 129600, random = 7200)
    public List<CategoryEntity> queryLvl2WithSubsByPid(Long pid){
        System.out.println("目标方法");
        // 没有命中，远程调用并放入缓存 2
        ResponseVo<List<CategoryEntity>> responseVo = this.pmsClient.queryCategoryLvl2WithSubsByPid(pid);
        List<CategoryEntity> categoryEntities = responseVo.getData();

        return categoryEntities;
    }

    public List<CategoryEntity> queryLvl2WithSubsByPid2(Long pid) {
        //查询缓存命中就直接返回：一级分类id作为key，以方法返回值作为value
        String json = this.redisTemplate.opsForValue().get(KEY_PREFIX + pid);
        if (StringUtils.isNotBlank(json)) {
            return JSON.parseArray(json, CategoryEntity.class);
        }
        RLock lock = this.redissonClient.getLock("lock" + pid);
        lock.lock();
        List<CategoryEntity> categoryEntities;
        try {
            String json2 = this.redisTemplate.opsForValue().get(KEY_PREFIX + pid);
            if (StringUtils.isNotBlank(json2)) {
                return JSON.parseArray(json2, CategoryEntity.class);
            }

            // 没有命中，远程调用并放入缓存 2
            ResponseVo<List<CategoryEntity>> responseVo = this.pmsClient.queryCategoryLvl2WithSubsByPid(pid);
            categoryEntities = responseVo.getData();

            if (CollectionUtils.isEmpty(categoryEntities)) {
                // 这里已经解决了缓存穿透问题
                this.redisTemplate.opsForValue().set(KEY_PREFIX + pid, JSON.toJSONString(categoryEntities), 5, TimeUnit.MINUTES);
            } else {
                // 这里为了解决缓存雪崩问题，要给缓存时间添加随机值
                this.redisTemplate.opsForValue().set(KEY_PREFIX + pid, JSON.toJSONString(categoryEntities), 90 + new Random().nextInt(5), TimeUnit.DAYS);
            }
            return categoryEntities;
        } finally {
            lock.unlock();
        }
    }
    public void testLock() throws InterruptedException{
        RLock lock = this.redissonClient.getLock("lock");
        try {
            lock.lock();
            //获取锁成功执行业务逻辑
            String countString = this.redisTemplate.opsForValue().get("count");
            if(StringUtils.isBlank(countString)){
                this.redisTemplate.opsForValue().set("count", "1");
            }
            int count = Integer.parseInt(countString);
            this.redisTemplate.opsForValue().set("count",String.valueOf(++count));
        } finally {
            lock.unlock();
        }

    }

    public void testLock3() throws InterruptedException{
        String uuid = UUID.randomUUID().toString();
        Boolean lock = this.distributedLock.tryLock("lock", uuid, 9l);
        if(lock){
            //获取锁成功执行业务逻辑
            String countString = this.redisTemplate.opsForValue().get("count");
            if(StringUtils.isBlank(countString)){
                this.redisTemplate.opsForValue().set("count", "1");
            }
            int count = Integer.parseInt(countString);
            this.redisTemplate.opsForValue().set("count",String.valueOf(++count));
            TimeUnit.SECONDS.sleep(60);

            this.testSubLock(uuid);
            this.distributedLock.unlock("lock",uuid);
        }

    }
    public void testSubLock(String uuid) {
        Boolean lock = this.distributedLock.tryLock("lock", uuid, 9l);

        System.out.println("测试分布式锁的可重入。。。。。。");

        this.distributedLock.unlock("lock", uuid);
    }

    public void testLock1() throws InterruptedException {
        //从redis中获取锁,相当于setnx
        String uuid = UUID.randomUUID().toString();
        Boolean lock = this.redisTemplate.opsForValue().setIfAbsent("lock", "uuid",3,TimeUnit.SECONDS);
        if(!lock){
            Thread.sleep(20);
            this.testLock();
        }else {
            //this.redisTemplate.expire("lock", 3,TimeUnit.SECONDS);
            //获取锁成功执行业务逻辑
            String countString = this.redisTemplate.opsForValue().get("count");
            if(StringUtils.isBlank(countString)){
                this.redisTemplate.opsForValue().set("count", "1");
            }
            int count = Integer.parseInt(countString);
            this.redisTemplate.opsForValue().set("count",String.valueOf(++count));
            //业务执行完之后释放锁
            String script = "if(redis.call('get', KEYS[1])==ARGV[1]) then return redis.call('del', KEYS[1]) else return 0 end";
            this.redisTemplate.execute(new DefaultRedisScript<>(script,Boolean.class), Arrays.asList("lock"), uuid);
            //判断是否是自己的锁如果是自己的锁才能释放
//            if(StringUtils.equals(uuid,this.redisTemplate.opsForValue().get("lock"))){
//                //判断完成之后，过期时间刚好到期，到导致该锁自动释放，此时再去执行delete会导致误删
//            this.redisTemplate.delete("lock");
//            }
        }

    }
}
