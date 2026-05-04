package com.axin.axinagent.infrastructure.storage;

import java.io.IOException;

/**
 * 对象存储抽象接口。
 */
public interface StorageService {

    /**
     * 保存文件，返回可访问的相对路径。
     *
     * @param category 分类目录
     * @param filename 文件名
     * @param content  文件内容
     * @return 相对访问路径
     */
    String saveFile(String category, String filename, byte[] content) throws IOException;

    /**
     * 获取文件在本地文件系统中的绝对路径。
     *
     * @param category 分类目录
     * @param filename 文件名
     * @return 文件绝对路径
     */
    String getFilePath(String category, String filename);
}
