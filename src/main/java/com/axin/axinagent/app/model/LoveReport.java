package com.axin.axinagent.app.model;

import lombok.Data;

import java.util.List;

@Data
public class LoveReport {

	private String title;

	private List<String> suggestions;
}
