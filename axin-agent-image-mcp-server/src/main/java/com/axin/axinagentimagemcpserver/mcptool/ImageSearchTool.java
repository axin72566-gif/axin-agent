package com.axin.axinagentimagemcpserver.mcptool;

import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class ImageSearchTool {

	// 替换为你的 Pexels API 密钥（需从官网申请）
	private static final String API_KEY = "uXU3733JfIxKguj5cwGYkW2glGCxMRBHf9stV4yyFEl3TGUhdEF0FcBG";

	// Pexels 常规搜索接口（请以文档为准）
	private static final String API_URL = "https://api.pexels.com/v1/search";

	@Tool(description = "Search images from web, return image URLs. You MUST output every URL in your reply without omission.")
	public String searchImage(@ToolParam(description = "Search query keyword, use English") String query) {
		try {
			List<String> urls = searchMediumImages(query);
			if (urls.isEmpty()) {
				return "No images found for: " + query;
			}
			// 逐行返回，并在开头附加指令，强制 AI 原样透传所有 URL
			StringBuilder sb = new StringBuilder();
			sb.append("搜索到以下图片URL，请将每一条URL完整展示给用户，不得省略：\n");
			for (String url : urls) {
				sb.append(url).append("\n");
			}
			return sb.toString();
		} catch (Exception e) {
			return "Error search image: " + e.getMessage();
		}
	}

	/**
	 * 搜索中等尺寸的图片列表
	 *
	 * @param query 查询关键字
	 * @return 图片列表
	 */
	public List<String> searchMediumImages(String query) {
		// 设置请求头（包含API密钥）
		Map<String, String> headers = new HashMap<>();
		headers.put("Authorization", API_KEY);

		// 设置请求参数（仅包含query，可根据文档补充page、per_page等参数）
		Map<String, Object> params = new HashMap<>();
		params.put("query", query);

		// 发送 GET 请求
		String response = HttpUtil.createGet(API_URL)
				.addHeaders(headers)
				.form(params)
				.execute()
				.body();

		// 解析响应JSON（假设响应结构包含"photos"数组，每个元素包含"medium"字段）
		return JSONUtil.parseObj(response)
				.getJSONArray("photos")
				.stream()
				.map(photoObj -> (JSONObject) photoObj)
				.map(photoObj -> photoObj.getJSONObject("src"))
				.map(photo -> photo.getStr("medium"))
				.filter(StrUtil::isNotBlank)
				.collect(Collectors.toList());
	}
}
