package com.atguigu.gmall.search.pojo;

import lombok.Data;

import java.util.List;

/**
 * 接受页面传递过来的检索参数
 * search?keyword=小米&brandId=1,3&cid=225&props=5:高通-麒麟&props=6:
 * 骁龙865-硅谷1000&sort=1&priceFrom=1000&priceTo=6000&pageNum=1&store=true
 *
 */
@Data
public class SearchParamVo {

    // 搜索关键字
    private String keyword;

    // 接收品牌id的过滤条件
    private List<Long> brandId;

    // 接收分类的过滤条件
    private List<Long> cid3;

    // 接收规格参数的过滤条件 5:128G-256G-521G
    private List<String> props;

    // 排序：1-价格升序 2-价格降序 3-新品降序 4-销量降序
    private Integer sort=0;

    // 价格区间
    private Double priceFrom;
    private Double priceTo;

    // 是否有货
    private Boolean store;

    // 分页数据
    private Integer pageNum = 1;
    private final Integer pageSize = 20;
}
