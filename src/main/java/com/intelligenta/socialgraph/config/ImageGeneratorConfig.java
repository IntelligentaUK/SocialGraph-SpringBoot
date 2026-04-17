package com.intelligenta.socialgraph.config;

import com.intelligenta.socialgraph.ai.DefaultModelCatalog;
import com.intelligenta.socialgraph.ai.DefaultModelCatalog.Capability;
import com.intelligenta.socialgraph.ai.ImageGenerator;
import com.intelligenta.socialgraph.ai.image.NoopImageGenerator;
import com.intelligenta.socialgraph.ai.image.SpringAiImageGenerator;
import org.springframework.ai.image.ImageModel;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Routes {@code ai.image.provider} to an {@link ImageGenerator} bean. */
@Configuration
public class ImageGeneratorConfig {

    @Configuration
    @ConditionalOnProperty(prefix = "ai.image", name = "provider", havingValue = "none", matchIfMissing = true)
    static class None {
        @Bean
        @ConditionalOnMissingBean(ImageGenerator.class)
        public ImageGenerator imageGenerator() {
            return new NoopImageGenerator();
        }
    }

    @Configuration
    @ConditionalOnProperty(prefix = "ai.image", name = "provider", havingValue = "openai")
    @ImportAutoConfiguration(org.springframework.ai.model.openai.autoconfigure.OpenAiImageAutoConfiguration.class)
    static class OpenAi {
        @Bean
        @ConditionalOnMissingBean(ImageGenerator.class)
        public ImageGenerator imageGenerator(ImageModel model, AiProperties props) {
            var r = DefaultModelCatalog.resolve(Capability.IMAGE, "openai", props.getImage());
            return new SpringAiImageGenerator(model, "openai", r.model());
        }
    }

    @Configuration
    @ConditionalOnProperty(prefix = "ai.image", name = "provider", havingValue = "azure-openai")
    @ImportAutoConfiguration(org.springframework.ai.model.azure.openai.autoconfigure.AzureOpenAiImageAutoConfiguration.class)
    static class AzureOpenAi {
        @Bean
        @ConditionalOnMissingBean(ImageGenerator.class)
        public ImageGenerator imageGenerator(ImageModel model, AiProperties props) {
            var r = DefaultModelCatalog.resolve(Capability.IMAGE, "azure-openai", props.getImage());
            return new SpringAiImageGenerator(model, "azure-openai", r.model());
        }
    }

    @Configuration
    @ConditionalOnProperty(prefix = "ai.image", name = "provider", havingValue = "stability-ai")
    @ImportAutoConfiguration(org.springframework.ai.model.stabilityai.autoconfigure.StabilityAiImageAutoConfiguration.class)
    static class StabilityAi {
        @Bean
        @ConditionalOnMissingBean(ImageGenerator.class)
        public ImageGenerator imageGenerator(ImageModel model, AiProperties props) {
            var r = DefaultModelCatalog.resolve(Capability.IMAGE, "stability-ai", props.getImage());
            return new SpringAiImageGenerator(model, "stability-ai", r.model());
        }
    }

    @Configuration
    @ConditionalOnProperty(prefix = "ai.image", name = "provider", havingValue = "zhipuai")
    @ImportAutoConfiguration(org.springframework.ai.model.zhipuai.autoconfigure.ZhiPuAiImageAutoConfiguration.class)
    static class ZhiPuAi {
        @Bean
        @ConditionalOnMissingBean(ImageGenerator.class)
        public ImageGenerator imageGenerator(ImageModel model, AiProperties props) {
            var r = DefaultModelCatalog.resolve(Capability.IMAGE, "zhipuai", props.getImage());
            return new SpringAiImageGenerator(model, "zhipuai", r.model());
        }
    }
}
