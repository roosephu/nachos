	.file	1 "sort.c"
	.text
	.align	2
	.globl	swap
	.ent	swap
swap:
	.frame	$fp,16,$31		# vars= 8, regs= 1/0, args= 0, extra= 0
	.mask	0x40000000,-8
	.fmask	0x00000000,0
	subu	$sp,$sp,16
	sw	$fp,8($sp)
	move	$fp,$sp
	sw	$4,16($fp)
	sw	$5,20($fp)
	lw	$2,16($fp)
	lw	$2,0($2)
	sw	$2,0($fp)
	lw	$3,16($fp)
	lw	$2,20($fp)
	lw	$2,0($2)
	sw	$2,0($3)
	lw	$3,20($fp)
	lw	$2,0($fp)
	sw	$2,0($3)
	move	$sp,$fp
	lw	$fp,8($sp)
	addu	$sp,$sp,16
	j	$31
	.end	swap
	.align	2
	.globl	main
	.ent	main
main:
	.frame	$fp,40,$31		# vars= 16, regs= 2/0, args= 16, extra= 0
	.mask	0xc0000000,-4
	.fmask	0x00000000,0
	subu	$sp,$sp,40
	sw	$31,36($sp)
	sw	$fp,32($sp)
	move	$fp,$sp
	jal	__main
	sw	$0,16($fp)
$L3:
	lw	$2,16($fp)
	slt	$2,$2,256
	bne	$2,$0,$L6
	j	$L4
$L6:
	lw	$2,16($fp)
	sll	$3,$2,2
	la	$2,array
	addu	$4,$3,$2
	li	$3,255			# 0xff
	lw	$2,16($fp)
	subu	$2,$3,$2
	sw	$2,0($4)
	lw	$2,16($fp)
	addu	$2,$2,1
	sw	$2,16($fp)
	j	$L3
$L4:
	sw	$0,16($fp)
$L7:
	lw	$2,16($fp)
	slt	$2,$2,255
	bne	$2,$0,$L10
	j	$L8
$L10:
	lw	$2,16($fp)
	sw	$2,20($fp)
$L11:
	lw	$2,20($fp)
	slt	$2,$2,256
	bne	$2,$0,$L14
	j	$L9
$L14:
	lw	$2,16($fp)
	sll	$3,$2,2
	la	$2,array
	addu	$4,$3,$2
	lw	$2,20($fp)
	sll	$3,$2,2
	la	$2,array
	addu	$2,$3,$2
	lw	$3,0($4)
	lw	$2,0($2)
	slt	$2,$2,$3
	beq	$2,$0,$L13
	lw	$2,16($fp)
	sll	$3,$2,2
	la	$2,array
	addu	$4,$3,$2
	lw	$2,20($fp)
	sll	$3,$2,2
	la	$2,array
	addu	$2,$3,$2
	move	$5,$2
	jal	swap
$L13:
	lw	$2,20($fp)
	addu	$2,$2,1
	sw	$2,20($fp)
	j	$L11
$L9:
	lw	$2,16($fp)
	addu	$2,$2,1
	sw	$2,16($fp)
	j	$L7
$L8:
	sw	$0,16($fp)
$L16:
	lw	$2,16($fp)
	slt	$2,$2,256
	bne	$2,$0,$L19
	j	$L17
$L19:
	lw	$2,16($fp)
	sll	$3,$2,2
	la	$2,array
	addu	$2,$3,$2
	lw	$3,0($2)
	lw	$2,16($fp)
	beq	$3,$2,$L18
	li	$2,1			# 0x1
	sw	$2,24($fp)
	j	$L2
$L18:
	lw	$2,16($fp)
	addu	$2,$2,1
	sw	$2,16($fp)
	j	$L16
$L17:
	sw	$0,24($fp)
$L2:
	lw	$2,24($fp)
	move	$sp,$fp
	lw	$31,36($sp)
	lw	$fp,32($sp)
	addu	$sp,$sp,40
	j	$31
	.end	main

	.comm	array,1024
