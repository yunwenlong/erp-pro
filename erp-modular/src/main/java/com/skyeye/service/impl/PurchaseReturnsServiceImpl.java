package com.skyeye.service.impl;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.github.miemiedev.mybatis.paginator.domain.PageBounds;
import com.github.miemiedev.mybatis.paginator.domain.PageList;
import com.skyeye.common.object.InputObject;
import com.skyeye.common.object.OutputObject;
import com.skyeye.common.util.ExcelUtil;
import com.skyeye.common.util.ToolUtil;
import com.skyeye.dao.PurchaseReturnsDao;
import com.skyeye.erp.util.ErpConstants;
import com.skyeye.erp.util.ErpOrderNum;
import com.skyeye.service.PurchaseReturnsService;

import net.sf.json.JSONArray;

@Service
public class PurchaseReturnsServiceImpl implements PurchaseReturnsService{
	
	@Autowired
	private PurchaseReturnsDao purchaseReturnsDao;

	/**
     * 获取采购退货列表信息
     * @param inputObject
     * @param outputObject
     * @throws Exception
     */
	@Override
	public void queryPurchaseReturnsToList(InputObject inputObject, OutputObject outputObject) throws Exception {
		Map<String, Object> params = inputObject.getParams();
        params.put("userId", inputObject.getLogParams().get("id"));
        List<Map<String, Object>> beans = purchaseReturnsDao.queryPurchaseReturnsToList(params,
                new PageBounds(Integer.parseInt(params.get("page").toString()), Integer.parseInt(params.get("limit").toString())));
        PageList<Map<String, Object>> beansPageList = (PageList<Map<String, Object>>)beans;
        int total = beansPageList.getPaginator().getTotalCount();
        outputObject.setBeans(beans);
        outputObject.settotal(total);
	}

	/**
     * 新增采购退货信息
     * @param inputObject
     * @param outputObject
     * @throws Exception
     */
	@SuppressWarnings("unchecked")
	@Override
	@Transactional(value="transactionManager")
	public void insertPurchaseReturnsMation(InputObject inputObject, OutputObject outputObject) throws Exception {
		Map<String, Object> map = inputObject.getParams();
		String depotheadStr = map.get("depotheadStr").toString();//采购产品列表
		String otherMoneyList = map.get("otherMoneyList").toString();//采购项目费用列表
		if(ToolUtil.isJson(depotheadStr) && ToolUtil.isJson(otherMoneyList)){
			String useId = ToolUtil.getSurFaceId();//单据主表id
			String userId = inputObject.getLogParams().get("id").toString();
			//处理数据
			JSONArray jArray = JSONArray.fromObject(depotheadStr);
			//产品中间转换对象，单据子表存储对象
			Map<String, Object> bean, entity;
			List<Map<String, Object>> entitys = new ArrayList<>();//单据子表实体集合信息
			BigDecimal allPrice = new BigDecimal("0");//主单总价
			BigDecimal taxLastMoneyPrice = new BigDecimal("0");//价税合计
			BigDecimal itemAllPrice = null;//子单对象
			for(int i = 0; i < jArray.size(); i++){
				bean = jArray.getJSONObject(i);
				entity = purchaseReturnsDao.queryMaterialsById(bean);
				if(entity != null && !entity.isEmpty()){
					//获取单价
					itemAllPrice = new BigDecimal(bean.get("unitPrice").toString());
					entity.put("id", ToolUtil.getSurFaceId());
					entity.put("headerId", useId);//单据主表id
					entity.put("operNumber", bean.get("rkNum"));//数量
					//计算子单总价：单价*数量
					itemAllPrice = itemAllPrice.multiply(new BigDecimal(bean.get("rkNum").toString()));
					entity.put("allPrice", itemAllPrice);//单据子表总价
					
					entity.put("estimatePurchasePrice", bean.get("unitPrice"));//单价
					entity.put("taxRate", bean.get("taxRate"));//税率
					entity.put("taxMoney", bean.get("taxMoney"));//税额
					entity.put("taxUnitPrice", bean.get("taxUnitPrice"));//含税单价
					entity.put("taxLastMoney", bean.get("taxLastMoney"));//价税合计
					
					entity.put("remark", bean.get("remark"));//备注
					entity.put("depotId", bean.get("depotId"));//仓库
					entity.put("mType", 0);//商品类型  0.普通  1.组合件  2.普通子件
					entity.put("userId", userId);
					entity.put("deleteFlag", 0);//删除标记，0未删除，1删除
					entitys.add(entity);
					//计算主单总价
					allPrice = allPrice.add(itemAllPrice);
					//计算价税合计金额
					taxLastMoneyPrice = taxLastMoneyPrice.add(new BigDecimal(bean.get("taxLastMoney").toString()));
				}
			}
			if(entitys.size() == 0){
				outputObject.setreturnMessage("请选择产品");
				return;
			}
			//单据主表对象
			Map<String, Object> depothead = new HashMap<>();
			depothead.put("id", useId);
			depothead.put("type", 1);//类型(1.出库/2.入库3.其他)
			depothead.put("subType", ErpConstants.DepoTheadSubType.OUT_IS_PURCHASE_RETURNS.getNum());//采购退货
			ErpOrderNum erpOrderNum = new ErpOrderNum();
			String orderNum = erpOrderNum.getOrderNumBySubType(userId, ErpConstants.DepoTheadSubType.OUT_IS_PURCHASE_RETURNS.getNum());
			depothead.put("defaultNumber", orderNum);//初始票据号
			depothead.put("number", orderNum);//票据号
			depothead.put("operPersonId", userId);//操作员id
			depothead.put("operPersonName", inputObject.getLogParams().get("userName"));//操作员名字
			depothead.put("createTime", ToolUtil.getTimeAndToString());//创建时间
			depothead.put("operTime", map.get("operTime"));//采购退货时间即单据日期
			depothead.put("organId", map.get("supplierId"));//供应商Id
			depothead.put("accountId", map.get("accountId"));//账户Id
			depothead.put("remark", map.get("remark"));//备注
			depothead.put("payType", map.get("payType"));//付款类型
			
			BigDecimal discountMoney = new BigDecimal(map.get("discountMoney").toString());//优惠金额
			depothead.put("discount", map.get("discount"));//优惠率
			depothead.put("discountMoney", map.get("discountMoney"));//优惠金额
			depothead.put("discountLastMoney", taxLastMoneyPrice.subtract(discountMoney));//优惠后金额
			depothead.put("changeAmount", map.get("changeAmount"));//本次付款金额
			depothead.put("otherMoney", map.get("otherMoney"));//采购费用合计
			depothead.put("otherMoneyList", map.get("otherMoneyList"));//采购费用涉及项目Id（包括快递、招待等）json串
			
			depothead.put("totalPrice", allPrice);//合计金额
			depothead.put("status", "2");//状态，0未审核、1.审核中、2.审核通过、3.审核拒绝、4.已转采购|销售
			depothead.put("userId", userId);
			depothead.put("deleteFlag", 0);//删除标记，0未删除，1删除
			purchaseReturnsDao.insertPurchaseReturnsMation(depothead);
			purchaseReturnsDao.insertPurchaseReturnsChildMation(entitys);
		}else{
			outputObject.setreturnMessage("数据格式错误");
		}
	}

	/**
     * 编辑采购退货信息时进行回显
     * @param inputObject
     * @param outputObject
     * @throws Exception
     */
	@Override
	public void queryPurchaseReturnsMationToEditById(InputObject inputObject, OutputObject outputObject) throws Exception {
		Map<String, Object> map = inputObject.getParams();
		map.put("userId", inputObject.getLogParams().get("id"));
		//获取主表信息
		Map<String, Object> bean = purchaseReturnsDao.queryPurchaseReturnsMationToEditById(map);
		if(bean != null && !bean.isEmpty()){
			//获取子表信息
			List<Map<String, Object>> norms = purchaseReturnsDao.queryPurchaseReturnsItemMationToEditById(bean);
			bean.put("items", norms);
			//获取采购项目列表
			if(bean.containsKey("otherMoneyList") && !ToolUtil.isBlank(bean.get("otherMoneyList").toString()) && ToolUtil.isJson(bean.get("otherMoneyList").toString())){
				bean.put("otherMoneyList", JSONArray.fromObject(bean.get("otherMoneyList").toString()));
			}else {
				bean.put("otherMoneyList", new JSONArray());
			}
			outputObject.setBean(bean);
			outputObject.settotal(1);
		}else{
			outputObject.setreturnMessage("该数据已不存在.");
		}
	}

	/**
     * 编辑采购退货信息
     * @param inputObject
     * @param outputObject
     * @throws Exception
     */
	@SuppressWarnings("unchecked")
	@Override
	@Transactional(value="transactionManager")
	public void editPurchaseReturnsMationById(InputObject inputObject, OutputObject outputObject) throws Exception {
		Map<String, Object> map = inputObject.getParams();
		String depotheadStr = map.get("depotheadStr").toString();//采购产品列表
		String otherMoneyList = map.get("otherMoneyList").toString();//采购项目费用列表
		if(ToolUtil.isJson(depotheadStr) && ToolUtil.isJson(otherMoneyList)){
			String useId = map.get("id").toString();//单据主表id
			String userId = inputObject.getLogParams().get("id").toString();
			//处理数据
			JSONArray jArray = JSONArray.fromObject(depotheadStr);
			//产品中间转换对象，单据子表存储对象
			Map<String, Object> bean, entity;
			List<Map<String, Object>> entitys = new ArrayList<>();//单据子表实体集合信息
			BigDecimal allPrice = new BigDecimal("0");//主单总价
			BigDecimal taxLastMoneyPrice = new BigDecimal("0");//价税合计
			BigDecimal itemAllPrice = null;//子单对象
			for(int i = 0; i < jArray.size(); i++){
				bean = jArray.getJSONObject(i);
				entity = purchaseReturnsDao.queryMaterialsById(bean);
				if(entity != null && !entity.isEmpty()){
					//获取单价
					itemAllPrice = new BigDecimal(bean.get("unitPrice").toString());
					entity.put("id", ToolUtil.getSurFaceId());
					entity.put("headerId", useId);//单据主表id
					entity.put("operNumber", bean.get("rkNum"));//数量
					//计算子单总价：单价*数量
					itemAllPrice = itemAllPrice.multiply(new BigDecimal(bean.get("rkNum").toString()));
					entity.put("allPrice", itemAllPrice);//单据子表总价
					
					entity.put("estimatePurchasePrice", bean.get("unitPrice"));//单价
					entity.put("taxRate", bean.get("taxRate"));//税率
					entity.put("taxMoney", bean.get("taxMoney"));//税额
					entity.put("taxUnitPrice", bean.get("taxUnitPrice"));//含税单价
					entity.put("taxLastMoney", bean.get("taxLastMoney"));//价税合计
					
					entity.put("remark", bean.get("remark"));//备注
					entity.put("depotId", bean.get("depotId"));//仓库
					entity.put("mType", 0);//商品类型  0.普通  1.组合件  2.普通子件
					entity.put("userId", userId);
					entity.put("deleteFlag", 0);//删除标记，0未删除，1删除
					entitys.add(entity);
					//计算主单总价
					allPrice = allPrice.add(itemAllPrice);
					//计算价税合计金额
					taxLastMoneyPrice = taxLastMoneyPrice.add(new BigDecimal(bean.get("taxLastMoney").toString()));
				}
			}
			if(entitys.size() == 0){
				outputObject.setreturnMessage("请选择产品");
				return;
			}
			//单据主表对象
			Map<String, Object> depothead = new HashMap<>();
			depothead.put("id", useId);
			depothead.put("operTime", map.get("operTime"));//采购退货时间即单据日期
			depothead.put("organId", map.get("supplierId"));//供应商Id
			depothead.put("accountId", map.get("accountId"));//账户Id
			depothead.put("remark", map.get("remark"));//备注
			depothead.put("payType", map.get("payType"));//付款类型
			
			BigDecimal discountMoney = new BigDecimal(map.get("discountMoney").toString());//优惠金额
			depothead.put("discount", map.get("discount"));//优惠率
			depothead.put("discountMoney", map.get("discountMoney"));//优惠金额
			depothead.put("discountLastMoney", taxLastMoneyPrice.subtract(discountMoney));//优惠后金额
			depothead.put("changeAmount", map.get("changeAmount"));//本次付款金额
			depothead.put("otherMoney", map.get("otherMoney"));//采购费用合计
			depothead.put("otherMoneyList", map.get("otherMoneyList"));//采购费用涉及项目Id（包括快递、招待等）json串
			
			depothead.put("totalPrice", allPrice);//合计金额
			depothead.put("userId", userId);
			//删除之前绑定的产品
			purchaseReturnsDao.deletePurchaseReturnsChildMation(map);
			//重新添加
			purchaseReturnsDao.editPurchaseReturnsMationById(depothead);
			purchaseReturnsDao.insertPurchaseReturnsChildMation(entitys);
		}else{
			outputObject.setreturnMessage("数据格式错误");
		}
	}

	/**
     * 导出Excel
     * @param inputObject
     * @param outputObject
     * @throws Exception
     */
	@SuppressWarnings("static-access")
	@Override
	public void queryMationToExcel(InputObject inputObject, OutputObject outputObject) throws Exception {
		Map<String, Object> params = inputObject.getParams();
        params.put("userId", inputObject.getLogParams().get("id"));
        List<Map<String, Object>> beans = purchaseReturnsDao.queryMationToExcel(params);
        String defaultNumber, linkNumber, status;
        for(Map<String, Object> bean : beans){
        	defaultNumber = bean.get("defaultNumber").toString();
        	linkNumber = bean.get("linkNumber").toString();
        	status = bean.get("status").toString();
        	if(!ToolUtil.isBlank(linkNumber)){
        		defaultNumber += "[转]";
        		if("2".equals(status)){
        			defaultNumber += "[正常]";
        		}else{
        			defaultNumber += "[预警]";
        		}
        	}
        	bean.put("defaultNumber", defaultNumber);
        }
        String[] key = new String[]{"defaultNumber", "supplierName", "materialNames", "totalPrice", "taxMoney", "discountLastMoney", "changeAmount", "operPersonName", "operTime"};
        String[] column = new String[]{"单据编号", "供应商", "关联产品", "合计金额", "含税合计", "优惠后金额", "付款", "操作人", "单据日期"};
        String[] dataType = new String[]{"", "data", "data", "data", "data", "data", "data", "data", "data"};
        //采购退货单信息导出
        ExcelUtil.createWorkBook("采购退货单", "采购退货单详细", beans, key, column, dataType, inputObject.getResponse()); 
	}
	
}
