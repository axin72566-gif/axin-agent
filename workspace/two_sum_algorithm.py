def two_sum(nums, target):
    # 创建一个字典来存储数值及其对应的索引
    num_to_index = {}
    
    # 遍历列表的同时构建字典并检查目标配对
    for index, num in enumerate(nums):
        complement = target - num  # 计算补数
        if complement in num_to_index:
            # 如果找到了补数，那么就找到了解决方案
            return [num_to_index[complement], index]
        num_to_index[num] = index  # 将当前数及其索引存入字典
    
    # 如果没有找到任何配对，抛出异常或返回 None
    raise ValueError("No two sum solution")

# 示例用法
nums_example = [2, 7, 11, 15]
target_example = 9
print(two_sum(nums_example, target_example))  # 输出: [0, 1]