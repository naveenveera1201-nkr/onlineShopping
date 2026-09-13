package com.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.IOException;

@Service
public class S3Service {

	private final S3Client s3Client;

	@Value("${aws.s3.bucketName}")
	private String bucketName;

	public S3Service(S3Client s3Client) {
		this.s3Client = s3Client;
	}

	// Upload image to S3 bucket
	public String uploadImage(String fileName, MultipartFile file) throws IOException {
		
		PutObjectRequest putObjectRequest = PutObjectRequest.builder().bucket(bucketName).key(fileName)
				.contentType(file.getContentType()).build();

		s3Client.putObject(putObjectRequest, RequestBody.fromBytes(file.getBytes()));
		return "Image uploaded successfully: " + fileName;
	}

	// Download/retrieve image bytes from S3 bucket
	public byte[] downloadImage(String fileName) {
		GetObjectRequest getObjectRequest = GetObjectRequest.builder().bucket(bucketName).key(fileName).build();

		ResponseBytes<GetObjectResponse> objectBytes = s3Client.getObjectAsBytes(getObjectRequest);
		return objectBytes.asByteArray();
	}
}



//
//import com.example.s3demo.service.S3Service;
//import org.springframework.http.HttpHeaders;
//import org.springframework.http.HttpStatus;
//import org.springframework.http.MediaType;
//import org.springframework.http.ResponseEntity;
//import org.springframework.web.bind.annotation.*;
//import org.springframework.web.multipart.MultipartFile;
//
//import java.io.IOException;
//
//@RestController
//@RequestMapping("/api/images")
//public class ImageController {
//
//    private final S3Service s3Service;
//
//    public ImageController(S3Service s3Service) {
//        this.s3Service = s3Service;
//    }
//
//    // Endpoint to upload an image
//    @PostMapping("/upload")
//    public ResponseEntity<String> uploadImage(
//            @RequestParam("file") MultipartFile file,
//            @RequestParam("name") String fileName) {
//        try {
//            String response = s3Service.uploadImage(fileName, file);
//            return new ResponseEntity<>(response, HttpStatus.OK);
//        } catch (IOException e) {
//            return new ResponseEntity<>("Failed to upload image: " + e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
//        }
//    }
//
//    // Endpoint to retrieve/view an image
//    @getMapping("/download/{fileName}")
//    public ResponseEntity<byte[]> downloadImage(@PathVariable String fileName) {
//        byte[] imageBytes = s3Service.downloadImage(fileName);
//        
//        HttpHeaders headers = new HttpHeaders();
//        headers.setContentType(MediaType.IMAGE_JPEG); // Change type dynamically if needed (e.g., IMAGE_PNG)
//        headers.setContentDispositionFormData("attachment", fileName);
//
//        return new ResponseEntity<>(imageBytes, headers, HttpStatus.OK);
//    }
//}