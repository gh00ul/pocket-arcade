class_name HdrPlan
extends RefCounted
## engine/gl/HdrPipeline.kt HdrPlan: chooses the picture from the capability set. Anything doubtful
## means [constant Pipeline.LDR]:
## - never for [constant GfxQuality.Tier.BATTERY], and only where the current ladder rung allows it;
## - never once [HdrGuard] has blocked it (a framebuffer or a probe failed before);
## - a device that can't render to float targets stays LDR;
## - where multisampling is wanted, HDR needs multisampled float renderbuffers; a driver that can
##   only do single-sampled float keeps the LDR picture with its anti-aliasing rather than trade
##   jagged edges for HDR. Where no multisampling is wanted, single-sampled HDR will do.


static func choose(caps: GlCaps, tier: int, rung_hdr: bool, samples_wanted: int, blocked: bool, msaa_blocked: bool) -> int:
	if caps == null or blocked or not rung_hdr or tier == GfxQuality.Tier.BATTERY or not caps.float_targets():
		return Pipeline.LDR
	if samples_wanted > 1:
		return Pipeline.HDR_MSAA if caps.float_msaa() and not msaa_blocked else Pipeline.LDR
	return Pipeline.HDR
