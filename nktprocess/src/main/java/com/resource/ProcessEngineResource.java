package com.resource;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

//@RequestMapping("/api")
public interface ProcessEngineResource {

	@PostMapping("/data")
	String process(@RequestParam("data") String data, @RequestParam("code") String code);

	/**
	 * File-upload sibling of {@link #process}, added for
	 * {@code EXCEL_STOCK_MASTER_IMPORT}. The single {@code /data} endpoint above
	 * only ever accepted {@code data} (JSON) + {@code code} — no multipart
	 * support — so rather than bolt a file onto that contract, this is a
	 * second, equally thin endpoint. It reads the uploaded file and folds it
	 * into the very same {@code data} map the normal endpoint uses, then hands
	 * off to {@code NktCoreService.process()} unchanged: every process code
	 * still goes through the same JWT/role/RequiredFields pipeline, only this
	 * one additionally carries a file.
	 */
	@PostMapping(value = "/data/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	String processUpload(@RequestParam("file") MultipartFile file,
	                      @RequestParam("data") String data,
	                      @RequestParam("code") String code);
}
