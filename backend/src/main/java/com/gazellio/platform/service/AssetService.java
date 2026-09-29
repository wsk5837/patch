package com.gazellio.platform.service;
import com.gazellio.platform.dto.ApiDtos.AssetView;
import com.gazellio.platform.repository.AssetRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.List;
@Service @RequiredArgsConstructor public class AssetService {private final AssetRepository assets;private final ViewService view;public List<AssetView> list(){return assets.findByActiveTrueOrderByNameAsc().stream().map(view::asset).toList();}public AssetView get(Long id){return view.asset(assets.findById(id).orElseThrow());}}
