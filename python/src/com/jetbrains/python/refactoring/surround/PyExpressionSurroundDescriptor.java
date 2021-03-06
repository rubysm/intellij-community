// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.refactoring.surround;

import com.intellij.lang.surroundWith.SurroundDescriptor;
import com.intellij.lang.surroundWith.Surrounder;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.refactoring.PyRefactoringUtil;
import com.jetbrains.python.refactoring.surround.surrounders.expressions.*;
import com.jetbrains.python.refactoring.surround.surrounders.expressions.PyLenExpressionStatementSurrounder;
import org.jetbrains.annotations.NotNull;

public class PyExpressionSurroundDescriptor implements SurroundDescriptor {
  private static final Surrounder[] SURROUNDERS = {new PyWithParenthesesSurrounder(), new PyIfExpressionSurrounder(),
    new PyWhileExpressionSurrounder(), new PyIsNoneSurrounder(), new PyIsNotNoneSurrounder(), new PyLenExpressionStatementSurrounder()};

  @Override
  @NotNull
  public PsiElement[] getElementsToSurround(PsiFile file, int startOffset, int endOffset) {
    final PsiElement element = PyRefactoringUtil.findExpressionInRange(file, startOffset, endOffset);
    if (!(element instanceof PyExpression)) {
      return PsiElement.EMPTY_ARRAY;
    }
    return new PsiElement[]{element};
  }

  @Override
  @NotNull
  public Surrounder[] getSurrounders() {
    return SURROUNDERS;
  }

  @Override
  public boolean isExclusive() {
    return false;
  }
}
