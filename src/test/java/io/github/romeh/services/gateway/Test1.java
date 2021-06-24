package io.github.romeh.services.gateway;

import static org.mockserver.model.HttpResponse.response;

import java.util.concurrent.TimeUnit;

import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;
import org.junit.runner.RunWith;
import org.mockserver.client.MockServerClient;
import org.mockserver.matchers.Times;
import org.mockserver.model.HttpRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;
import org.testcontainers.containers.MockServerContainer;
import org.testcontainers.utility.DockerImageName;

import com.carrotsearch.junitbenchmarks.BenchmarkRule;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT)
@RunWith(SpringRunner.class)
@ContextConfiguration(initializers = { Test1.Initializer.class })
public class Test1 {
	
	private static MockServerContainer mockServerContainer;
	private static MockServerClient client ;
	
	static {
		DockerImageName din = DockerImageName.parse("jamesdbloom/mockserver:mockserver-5.5.4");
		mockServerContainer = new MockServerContainer();
		mockServerContainer.start();
		client = new MockServerClient(mockServerContainer.getContainerIpAddress(), mockServerContainer.getServerPort());
	}

	static class Initializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {
		public void initialize(ConfigurableApplicationContext configurableApplicationContext) {
			TestPropertyValues
					.of("spring.cloud.gateway.routes[0].id=test-service-withResilient4j",
							"spring.cloud.gateway.routes[0].uri=" + mockServerContainer.getEndpoint(),
							"spring.cloud.gateway.routes[0].predicates[0]=" + "Path=/testService/**",
							"spring.cloud.gateway.routes[0].filters[0]="
									+ "RewritePath=/testService/(?<path>.*), /$\\{path}",
							"spring.cloud.gateway.routes[0].filters[1].name=" + "CircuitBreaker",
							"spring.cloud.gateway.routes[0].filters[1].args.name=" + "backendA",
							"spring.cloud.gateway.routes[0].filters[1].args.fallbackUri="
									+ "forward:/fallback/testService",
							"spring.cloud.gateway.routes[1].id=test-service-withResilient4j-statusCode",
							"spring.cloud.gateway.routes[1].uri=" + mockServerContainer.getEndpoint(),
							"spring.cloud.gateway.routes[1].predicates[0]=" + "Path=/testInternalServiceError/**",
							"spring.cloud.gateway.routes[1].filters[0]="
									+ "RewritePath=/testInternalServiceError/(?<path>.*), /$\\{path}",
							"spring.cloud.gateway.routes[1].filters[1].name=" + "CircuitBreaker",
							"spring.cloud.gateway.routes[1].filters[1].args.name=" + "backendB",
							"spring.cloud.gateway.routes[1].filters[1].args.fallbackUri="
									+ "forward:/fallback/testInternalServiceError",
							"spring.cloud.gateway.routes[1].filters[2]=StatusCodeCheck")
					.applyTo(configurableApplicationContext.getEnvironment());
		}
	}
	
	@Rule
	public TestRule benchmarkRun = new BenchmarkRule();
	@Autowired
	private TestRestTemplate template;
	private int testPathSelector = 0;

	@BeforeClass
	public static void init() {
		System.out.println("--------------------------------------------");
		client.when(HttpRequest.request()
				.withPath("/1"))
				.respond(response()
						.withBody("{\"msgCode\":\"1\",\"msg\":\"1000000\"}")
						.withHeader("Content-Type", "application/json"));
		client.when(HttpRequest.request()
				.withPath("/2"), Times.exactly(5))
				.respond(response()
						.withBody("{\"msgCode\":\"2\",\"msg\":\"2000000\"}")
						.withDelay(TimeUnit.MILLISECONDS, 200)
						.withHeader("Content-Type", "application/json"));
		client.when(HttpRequest.request()
				.withPath("/2"),Times.exactly(5))
				.respond(response().withStatusCode(500));
		client.when(HttpRequest.request()
				.withPath("/2"))
				.respond(response()
						.withBody("{\"msgCode\":\"2\",\"msg\":\"2100000\"}")
						.withHeader("Content-Type", "application/json"));
		System.out.println("++++++++++++++++++++++++++++++++++++++++++++++");

	}

	@Test
	public void test() {
		System.out.println("sds");
	}

}
