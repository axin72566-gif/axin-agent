package com.axin.axinagentimagemcpserver.mcptool;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ImageSearchToolTest {

	@Test
	void searchImage() {

		ImageSearchTool imageSearchTool = new ImageSearchTool();
		String result = imageSearchTool.searchImage("请帮我搜索一张关于猫的图片");
		System.out.println(result);
	}
}