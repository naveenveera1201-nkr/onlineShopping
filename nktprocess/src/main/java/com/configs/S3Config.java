package com.configs;

import org.springframework.context.annotation.Configuration;

@Configuration
public class S3Config {

//	@Value("${aws.accessKey}")
//	private String accessKey;
//
//	@Value("${aws.secretKey}")
//	private String secretKey;
//
//	@Value("${aws.region}")
//	private String region;
//
//	@Bean
//	public S3Client s3Client() {
//		AwsBasicCredentials credentials = AwsBasicCredentials.create(accessKey, secretKey);
//		return S3Client.builder().region(Region.of(region))
//				.credentialsProvider(StaticCredentialsProvider.create(credentials)).build();
//	}
}