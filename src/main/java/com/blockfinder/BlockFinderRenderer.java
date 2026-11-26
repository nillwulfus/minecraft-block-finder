package com.blockfinder;

import java.util.List;
import java.util.OptionalDouble;
import java.util.OptionalInt;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexFormat;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.system.MemoryUtil;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MappableRingBuffer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;

import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;

/**
 * Рисует полупрозрачные кубы поверх найденных блоков.
 * Два конвейера: со сквозной подсветкой (без теста глубины) и обычный (с тестом глубины).
 * Основа - пример из документации Fabric "Rendering in the World" для 1.21.11.
 */
public final class BlockFinderRenderer {
	private static final String ID = BlockFinderClient.MOD_ID;

	private static final RenderPipeline THROUGH_WALLS = RenderPipelines.register(
			RenderPipeline.builder(RenderPipelines.DEBUG_FILLED_SNIPPET)
					.withLocation(Identifier.fromNamespaceAndPath(ID, "pipeline/filled_through_walls"))
					.withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
					.build());

	private static final RenderPipeline WITH_DEPTH = RenderPipelines.register(
			RenderPipeline.builder(RenderPipelines.DEBUG_FILLED_SNIPPET)
					.withLocation(Identifier.fromNamespaceAndPath(ID, "pipeline/filled_with_depth"))
					.build());

	private static final ByteBufferBuilder ALLOCATOR = new ByteBufferBuilder(RenderType.SMALL_BUFFER_SIZE);
	private static final Vector4f COLOR_MODULATOR = new Vector4f(1f, 1f, 1f, 1f);
	private static final Vector3f MODEL_OFFSET = new Vector3f();
	private static final Matrix4f TEXTURE_MATRIX = new Matrix4f();

	private static BufferBuilder buffer;
	private static MappableRingBuffer vertexBuffer;

	private BlockFinderRenderer() {
	}

	/** Вызывается из WorldRenderEvents.BEFORE_TRANSLUCENT. */
	public static void render(WorldRenderContext context) {
		BlockFinderConfig cfg = BlockFinderClient.config();
		if (!cfg.enabled) {
			return;
		}
		List<BlockPos> positions = BlockScanner.results();
		if (positions.isEmpty()) {
			return;
		}

		RenderPipeline pipeline = cfg.throughWalls ? THROUGH_WALLS : WITH_DEPTH;
		// Чуть больше блока, чтобы грани не мерцали на стыке с поверхностью (актуально для режима с глубиной)
		float grow = cfg.throughWalls ? 0.0f : 0.002f;

		PoseStack matrices = context.matrices();
		Vec3 camera = context.worldState().cameraRenderState.pos;

		matrices.pushPose();
		matrices.translate(-camera.x, -camera.y, -camera.z);

		buffer = new BufferBuilder(ALLOCATOR, pipeline.getVertexFormatMode(), pipeline.getVertexFormat());
		float r = cfg.red();
		float g = cfg.green();
		float b = cfg.blue();
		float a = cfg.alpha();

		for (BlockPos pos : positions) {
			filledBox(matrices.last().pose(), buffer,
					pos.getX() - grow, pos.getY() - grow, pos.getZ() - grow,
					pos.getX() + 1 + grow, pos.getY() + 1 + grow, pos.getZ() + 1 + grow,
					r, g, b, a);
		}

		matrices.popPose();

		drawBuffer(Minecraft.getInstance(), pipeline);
	}

	private static void filledBox(Matrix4fc m, BufferBuilder buf,
			float minX, float minY, float minZ, float maxX, float maxY, float maxZ,
			float red, float green, float blue, float alpha) {
		// Front
		buf.addVertex(m, minX, minY, maxZ).setColor(red, green, blue, alpha);
		buf.addVertex(m, maxX, minY, maxZ).setColor(red, green, blue, alpha);
		buf.addVertex(m, maxX, maxY, maxZ).setColor(red, green, blue, alpha);
		buf.addVertex(m, minX, maxY, maxZ).setColor(red, green, blue, alpha);

		// Back
		buf.addVertex(m, maxX, minY, minZ).setColor(red, green, blue, alpha);
		buf.addVertex(m, minX, minY, minZ).setColor(red, green, blue, alpha);
		buf.addVertex(m, minX, maxY, minZ).setColor(red, green, blue, alpha);
		buf.addVertex(m, maxX, maxY, minZ).setColor(red, green, blue, alpha);

		// Left
		buf.addVertex(m, minX, minY, minZ).setColor(red, green, blue, alpha);
		buf.addVertex(m, minX, minY, maxZ).setColor(red, green, blue, alpha);
		buf.addVertex(m, minX, maxY, maxZ).setColor(red, green, blue, alpha);
		buf.addVertex(m, minX, maxY, minZ).setColor(red, green, blue, alpha);

		// Right
		buf.addVertex(m, maxX, minY, maxZ).setColor(red, green, blue, alpha);
		buf.addVertex(m, maxX, minY, minZ).setColor(red, green, blue, alpha);
		buf.addVertex(m, maxX, maxY, minZ).setColor(red, green, blue, alpha);
		buf.addVertex(m, maxX, maxY, maxZ).setColor(red, green, blue, alpha);

		// Top
		buf.addVertex(m, minX, maxY, maxZ).setColor(red, green, blue, alpha);
		buf.addVertex(m, maxX, maxY, maxZ).setColor(red, green, blue, alpha);
		buf.addVertex(m, maxX, maxY, minZ).setColor(red, green, blue, alpha);
		buf.addVertex(m, minX, maxY, minZ).setColor(red, green, blue, alpha);

		// Bottom
		buf.addVertex(m, minX, minY, minZ).setColor(red, green, blue, alpha);
		buf.addVertex(m, maxX, minY, minZ).setColor(red, green, blue, alpha);
		buf.addVertex(m, maxX, minY, maxZ).setColor(red, green, blue, alpha);
		buf.addVertex(m, minX, minY, maxZ).setColor(red, green, blue, alpha);
	}

	private static void drawBuffer(Minecraft client, RenderPipeline pipeline) {
		MeshData built = buffer.buildOrThrow();
		MeshData.DrawState drawState = built.drawState();
		VertexFormat format = drawState.format();

		GpuBuffer vertices = upload(drawState, format, built);
		draw(client, pipeline, built, drawState, vertices, format);

		vertexBuffer.rotate();
		buffer = null;
	}

	private static GpuBuffer upload(MeshData.DrawState drawState, VertexFormat format, MeshData built) {
		int size = drawState.vertexCount() * format.getVertexSize();

		if (vertexBuffer == null || vertexBuffer.size() < size) {
			if (vertexBuffer != null) {
				vertexBuffer.close();
			}
			vertexBuffer = new MappableRingBuffer(() -> ID + " highlight buffer",
					GpuBuffer.USAGE_VERTEX | GpuBuffer.USAGE_MAP_WRITE, size);
		}

		CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
		try (GpuBuffer.MappedView view = encoder.mapBuffer(
				vertexBuffer.currentBuffer().slice(0, built.vertexBuffer().remaining()), false, true)) {
			MemoryUtil.memCopy(built.vertexBuffer(), view.data());
		}
		return vertexBuffer.currentBuffer();
	}

	private static void draw(Minecraft client, RenderPipeline pipeline, MeshData built,
			MeshData.DrawState drawState, GpuBuffer vertices, VertexFormat format) {
		GpuBuffer indices;
		VertexFormat.IndexType indexType;

		if (pipeline.getVertexFormatMode() == VertexFormat.Mode.QUADS) {
			built.sortQuads(ALLOCATOR, RenderSystem.getProjectionType().vertexSorting());
			indices = pipeline.getVertexFormat().uploadImmediateIndexBuffer(built.indexBuffer());
			indexType = built.drawState().indexType();
		} else {
			RenderSystem.AutoStorageIndexBuffer shapeIndexBuffer = RenderSystem.getSequentialBuffer(pipeline.getVertexFormatMode());
			indices = shapeIndexBuffer.getBuffer(drawState.indexCount());
			indexType = shapeIndexBuffer.type();
		}

		GpuBufferSlice dynamicTransforms = RenderSystem.getDynamicUniforms()
				.writeTransform(RenderSystem.getModelViewMatrix(), COLOR_MODULATOR, MODEL_OFFSET, TEXTURE_MATRIX);

		try (RenderPass pass = RenderSystem.getDevice()
				.createCommandEncoder()
				.createRenderPass(() -> ID + " highlight pass",
						client.getMainRenderTarget().getColorTextureView(), OptionalInt.empty(),
						client.getMainRenderTarget().getDepthTextureView(), OptionalDouble.empty())) {
			pass.setPipeline(pipeline);
			RenderSystem.bindDefaultUniforms(pass);
			pass.setUniform("DynamicTransforms", dynamicTransforms);
			pass.setVertexBuffer(0, vertices);
			pass.setIndexBuffer(indices, indexType);
			pass.drawIndexed(0, 0, drawState.indexCount(), 1);
		}

		built.close();
	}

	/** Вызывается миксином при закрытии GameRenderer. */
	public static void close() {
		ALLOCATOR.close();
		if (vertexBuffer != null) {
			vertexBuffer.close();
			vertexBuffer = null;
		}
	}
}
